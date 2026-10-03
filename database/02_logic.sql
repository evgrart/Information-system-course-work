-- Функции SECURITY INVOKER: используются права вызывающей роли БД.
-- Поиск имён закреплён за схемой установки, а не search_path клиента.
CREATE FUNCTION lf_assert_user(p_user bigint, p_subscription boolean DEFAULT true)
RETURNS void LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM 1 FROM lf_users WHERE id=p_user AND state='verified' AND email_confirmed_at IS NOT NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Аккаунт не допущен' USING ERRCODE='P0001'; END IF;
    IF p_subscription AND NOT EXISTS (SELECT 1 FROM lf_subscriptions
      WHERE user_id=p_user AND starts_at<=clock_timestamp() AND ends_at>clock_timestamp()) THEN
      RAISE EXCEPTION 'Нет действующей подписки' USING ERRCODE='P0001';
    END IF;
END $$;
CREATE FUNCTION lf_assert_moderator(p_user bigint)
RETURNS void LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM lf_assert_user(p_user,false);
    IF NOT EXISTS (SELECT 1 FROM lf_user_roles WHERE user_id=p_user AND role_code IN ('moderator','admin')) THEN
      RAISE EXCEPTION 'Требуется роль модератора' USING ERRCODE='P0001';
    END IF;
END $$;
CREATE FUNCTION lf_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Изменение истории запрещено' USING ERRCODE='P0001'; END $$;
CREATE TRIGGER lf_audit_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON lf_audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION lf_immutable();
CREATE TRIGGER lf_bid_immutable BEFORE UPDATE OR DELETE ON lf_bids
    FOR EACH ROW EXECUTE FUNCTION lf_immutable();
CREATE TRIGGER lf_payment_event_immutable BEFORE UPDATE OR DELETE ON lf_payment_events
    FOR EACH ROW EXECUTE FUNCTION lf_immutable();

CREATE FUNCTION lf_audit_change() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_actor bigint;
BEGIN
    v_actor:=nullif(current_setting('lf.actor_id',true),'')::bigint;
    INSERT INTO lf_audit_entries(actor_id,action,entity_table,entity_id,details)
      VALUES (v_actor,TG_OP,TG_TABLE_NAME,NEW.id,
        jsonb_build_object('state',to_jsonb(NEW)->>'state'));
    RETURN NEW;
END $$;
CREATE TRIGGER lf_listing_audit AFTER INSERT OR UPDATE ON lf_listings FOR EACH ROW EXECUTE FUNCTION lf_audit_change();
CREATE TRIGGER lf_auction_audit AFTER INSERT OR UPDATE ON lf_auctions FOR EACH ROW EXECUTE FUNCTION lf_audit_change();
CREATE TRIGGER lf_payment_audit AFTER INSERT OR UPDATE ON lf_payment_orders FOR EACH ROW EXECUTE FUNCTION lf_audit_change();
CREATE TRIGGER lf_permission_audit AFTER INSERT OR UPDATE ON lf_auction_permissions FOR EACH ROW EXECUTE FUNCTION lf_audit_change();
CREATE TRIGGER lf_user_audit AFTER UPDATE ON lf_users FOR EACH ROW EXECUTE FUNCTION lf_audit_change();

CREATE FUNCTION lf_user_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.state IS DISTINCT FROM OLD.state OR NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
      NEW.token_version:=OLD.token_version+1;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_user_guard BEFORE UPDATE ON lf_users FOR EACH ROW EXECUTE FUNCTION lf_user_guard();
CREATE FUNCTION lf_blocked_auctions() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_listing bigint;
BEGIN
    IF NEW.state='blocked' AND OLD.state<>'blocked' THEN
      FOR v_listing IN SELECT l.id FROM lf_listings l WHERE EXISTS
        (SELECT 1 FROM lf_auctions a WHERE a.listing_id=l.id AND a.state IN ('scheduled','active') AND
         (a.seller_id=NEW.id OR EXISTS (SELECT 1 FROM lf_bids b WHERE b.auction_id=a.id AND b.bidder_id=NEW.id)))
        ORDER BY l.id FOR UPDATE LOOP
        UPDATE lf_auctions SET state='suspended' WHERE listing_id=v_listing AND state IN ('scheduled','active');
      END LOOP;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_user_block_auctions AFTER UPDATE ON lf_users FOR EACH ROW EXECUTE FUNCTION lf_blocked_auctions();
CREATE FUNCTION lf_verification_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.state<>'pending' THEN
      PERFORM lf_assert_moderator(NEW.reviewer_id);
      IF NEW.state='approved' THEN
        UPDATE lf_users SET state='verified' WHERE id=NEW.user_id AND email_confirmed_at IS NOT NULL AND state='pending';
        IF NOT FOUND THEN RAISE EXCEPTION 'Почта не подтверждена или профиль уже рассмотрен'; END IF;
      END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_verification_guard BEFORE INSERT OR UPDATE ON lf_verifications
    FOR EACH ROW EXECUTE FUNCTION lf_verification_guard();
CREATE FUNCTION lf_consume_token(p_hash varchar,p_purpose varchar) RETURNS bigint
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_token lf_verification_tokens%ROWTYPE;
BEGIN
    SELECT * INTO STRICT v_token FROM lf_verification_tokens WHERE token_hash=p_hash AND purpose=p_purpose FOR UPDATE;
    IF v_token.consumed_at IS NOT NULL OR v_token.expires_at<=clock_timestamp() THEN RAISE EXCEPTION 'Токен использован или просрочен'; END IF;
    PERFORM 1 FROM lf_users WHERE id=v_token.user_id AND state<>'blocked' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Аккаунт заблокирован'; END IF;
    UPDATE lf_verification_tokens SET consumed_at=clock_timestamp() WHERE id=v_token.id;
    IF p_purpose='email_confirm' THEN UPDATE lf_users SET email_confirmed_at=clock_timestamp() WHERE id=v_token.user_id; END IF;
    RETURN v_token.user_id;
END $$;

CREATE FUNCTION lf_payment_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_tariff lf_tariffs%ROWTYPE;
BEGIN
    IF TG_OP='INSERT' THEN
      PERFORM lf_assert_user(NEW.user_id,false);
      SELECT * INTO STRICT v_tariff FROM lf_tariffs WHERE id=NEW.tariff_id AND active;
      IF NEW.amount<>v_tariff.amount OR NEW.duration_days<>v_tariff.duration_days OR NEW.state<>'pending' THEN
        RAISE EXCEPTION 'Заказ не соответствует действующему тарифу';
      END IF;
    ELSE
      IF (NEW.user_id,NEW.tariff_id,NEW.request_key,NEW.amount,NEW.duration_days,NEW.currency)
          IS DISTINCT FROM (OLD.user_id,OLD.tariff_id,OLD.request_key,OLD.amount,OLD.duration_days,OLD.currency)
         OR OLD.state<>'pending' THEN RAISE EXCEPTION 'Параметры заказа зафиксированы'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_payment_guard BEFORE INSERT OR UPDATE ON lf_payment_orders FOR EACH ROW EXECUTE FUNCTION lf_payment_guard();
CREATE FUNCTION lf_subscription_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_order lf_payment_orders%ROWTYPE;
BEGIN
    PERFORM 1 FROM lf_users WHERE id=NEW.user_id FOR UPDATE;
    SELECT * INTO STRICT v_order FROM lf_payment_orders WHERE id=NEW.payment_order_id;
    IF v_order.state<>'paid' OR v_order.user_id<>NEW.user_id OR
       NEW.ends_at<>NEW.starts_at+make_interval(days=>v_order.duration_days) THEN
      RAISE EXCEPTION 'Подписка не соответствует оплаченному заказу';
    END IF;
    IF EXISTS (SELECT 1 FROM lf_subscriptions s WHERE s.user_id=NEW.user_id
      AND s.id<>NEW.id AND tstzrange(s.starts_at,s.ends_at,'[)') && tstzrange(NEW.starts_at,NEW.ends_at,'[)')) THEN
      RAISE EXCEPTION 'Периоды подписки пересекаются';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_subscription_guard BEFORE INSERT OR UPDATE ON lf_subscriptions
    FOR EACH ROW EXECUTE FUNCTION lf_subscription_guard();

CREATE FUNCTION lf_activate_subscription(p_order bigint,p_event varchar,p_amount numeric,p_currency char(3) DEFAULT 'RUB')
RETURNS bigint LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_order lf_payment_orders%ROWTYPE; v_event lf_payment_events%ROWTYPE; v_start timestamptz; v_id bigint;
BEGIN
    SELECT * INTO STRICT v_order FROM lf_payment_orders WHERE id=p_order;
    PERFORM 1 FROM lf_users WHERE id=v_order.user_id FOR UPDATE;
    SELECT * INTO STRICT v_order FROM lf_payment_orders WHERE id=p_order FOR UPDATE;
    IF p_amount IS NULL OR p_currency IS NULL OR p_amount<>v_order.amount OR p_currency<>v_order.currency THEN
      RAISE EXCEPTION 'Сумма или валюта события не совпадает с заказом';
    END IF;
    INSERT INTO lf_payment_events(provider_event_id,order_id,amount,currency)
      VALUES (p_event,p_order,p_amount,p_currency) ON CONFLICT DO NOTHING;
    SELECT * INTO STRICT v_event FROM lf_payment_events WHERE provider_event_id=p_event;
    IF v_event.order_id<>p_order OR v_event.amount<>p_amount OR v_event.currency<>p_currency THEN
      RAISE EXCEPTION 'Идентификатор события уже использован с другим содержимым';
    END IF;
    SELECT id INTO v_id FROM lf_subscriptions WHERE payment_order_id=p_order;
    IF FOUND THEN RETURN v_id; END IF;
    PERFORM lf_assert_user(v_order.user_id,false);
    IF v_order.state<>'pending' THEN RAISE EXCEPTION 'Заказ не ожидает оплаты'; END IF;
    SELECT greatest(clock_timestamp(),coalesce(max(ends_at),clock_timestamp())) INTO v_start
      FROM lf_subscriptions WHERE user_id=v_order.user_id;
    UPDATE lf_payment_orders SET state='paid',paid_at=clock_timestamp() WHERE id=p_order;
    INSERT INTO lf_subscriptions(user_id,payment_order_id,starts_at,ends_at)
      VALUES (v_order.user_id,p_order,v_start,v_start+make_interval(days=>v_order.duration_days)) RETURNING id INTO v_id;
    INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('subscription.activated',v_id,jsonb_build_object('user_id',v_order.user_id));
    INSERT INTO lf_notifications(user_id,kind,body) VALUES (v_order.user_id,'subscription','Подписка активирована');
    RETURN v_id;
END $$;

CREATE FUNCTION lf_listing_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.event_at>clock_timestamp() OR NEW.event_until>clock_timestamp() THEN RAISE EXCEPTION 'Время события не может быть в будущем'; END IF;
    IF TG_OP='INSERT' OR (TG_OP='UPDATE' AND NEW.state='pending' AND OLD.state<>'pending') THEN
      PERFORM lf_assert_user(NEW.author_id);
    END IF;
    IF NEW.state='published' AND (TG_OP='INSERT' OR OLD.state IN ('draft','pending','rejected')) THEN
      PERFORM lf_assert_moderator(NEW.moderator_id);
      IF NEW.moderator_id=NEW.author_id THEN RAISE EXCEPTION 'Самомодерация запрещена'; END IF;
    END IF;
    IF TG_OP='UPDATE' THEN
      IF NEW.author_id<>OLD.author_id OR NEW.kind<>OLD.kind THEN RAISE EXCEPTION 'Автор и тип объявления неизменяемы'; END IF;
      IF (NEW.title,NEW.description,NEW.category_id,NEW.location_id,NEW.custodian_org_id,NEW.official_reported_at,NEW.event_at,NEW.event_until)
         IS DISTINCT FROM (OLD.title,OLD.description,OLD.category_id,OLD.location_id,OLD.custodian_org_id,OLD.official_reported_at,OLD.event_at,OLD.event_until) THEN
        IF EXISTS (SELECT 1 FROM lf_auctions WHERE listing_id=OLD.id AND state IN ('scheduled','active','suspended')) OR
           OLD.state IN ('reserved','returned','auctioned') THEN RAISE EXCEPTION 'Объявление участвует в незавершённом процессе'; END IF;
        NEW.state:='pending'; NEW.moderator_id:=NULL; NEW.moderated_at:=NULL;
        PERFORM lf_assert_user(NEW.author_id);
      END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_listing_guard BEFORE INSERT OR UPDATE ON lf_listings FOR EACH ROW EXECUTE FUNCTION lf_listing_guard();

CREATE FUNCTION lf_claim_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_listing lf_listings%ROWTYPE;
BEGIN
    SELECT * INTO STRICT v_listing FROM lf_listings WHERE id=NEW.listing_id FOR UPDATE;
    IF TG_OP='INSERT' THEN
      PERFORM lf_assert_user(NEW.claimant_id);
      IF NEW.state<>'pending' OR v_listing.kind<>'found' OR v_listing.state<>'published' OR NEW.claimant_id=v_listing.author_id THEN
        RAISE EXCEPTION 'Нельзя подать заявку на эту находку';
      END IF;
      UPDATE lf_auctions SET state='suspended' WHERE listing_id=NEW.listing_id AND state IN ('scheduled','active');
    ELSE
      IF (NEW.listing_id,NEW.claimant_id) IS DISTINCT FROM (OLD.listing_id,OLD.claimant_id) THEN
        RAISE EXCEPTION 'Участники заявки неизменяемы';
      END IF;
      IF NEW.state<>OLD.state AND NOT
        ((OLD.state='pending' AND NEW.state IN ('accepted','rejected','cancelled')) OR
         (OLD.state='accepted' AND NEW.state IN ('fulfilled','cancelled'))) THEN
        RAISE EXCEPTION 'Недопустимый переход статуса заявки';
      END IF;
      IF NEW.state='accepted' AND OLD.state<>'accepted' THEN
        IF OLD.state<>'pending' OR v_listing.state<>'published' OR
          EXISTS (SELECT 1 FROM lf_auctions WHERE listing_id=NEW.listing_id AND state IN ('scheduled','active','suspended')) THEN
          RAISE EXCEPTION 'Находка недоступна для резервирования';
        END IF;
      END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_claim_guard BEFORE INSERT OR UPDATE ON lf_claims FOR EACH ROW EXECUTE FUNCTION lf_claim_guard();
CREATE FUNCTION lf_reserved_integrity() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_id bigint; v_state varchar; v_count integer;
BEGIN
    IF TG_TABLE_NAME='lf_listings' THEN v_id:=NEW.id; ELSE v_id:=coalesce(NEW.listing_id,OLD.listing_id); END IF;
    SELECT state INTO v_state FROM lf_listings WHERE id=v_id;
    SELECT count(*) INTO v_count FROM lf_claims WHERE listing_id=v_id AND state='accepted';
    IF (v_state='reserved' AND v_count<>1) OR (v_state<>'reserved' AND v_count<>0) THEN
      RAISE EXCEPTION 'Резерв должен соответствовать одной принятой заявке';
    END IF;
    IF v_state IN ('reserved','returned','auctioned','archived') AND
      EXISTS (SELECT 1 FROM lf_auctions WHERE listing_id=v_id AND state IN ('scheduled','active','suspended')) THEN
      RAISE EXCEPTION 'Находка не может одновременно участвовать в аукционе и возврате';
    END IF;
    IF v_state='returned' AND NOT EXISTS (SELECT 1 FROM lf_claims c JOIN lf_transfers t ON t.claim_id=c.id
      WHERE c.listing_id=v_id AND c.state='fulfilled' AND t.completed_at IS NOT NULL) THEN
      RAISE EXCEPTION 'Возврат требует двух подтверждений передачи';
    END IF;
    IF v_state='auctioned' AND NOT EXISTS (SELECT 1 FROM lf_auctions WHERE listing_id=v_id AND state='finished' AND winner_bid_id IS NOT NULL) THEN
      RAISE EXCEPTION 'Продажа требует завершённого аукциона с победителем';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER lf_listing_reserved AFTER INSERT OR UPDATE ON lf_listings DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION lf_reserved_integrity();
CREATE CONSTRAINT TRIGGER lf_claim_reserved AFTER INSERT OR UPDATE OR DELETE ON lf_claims DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION lf_reserved_integrity();
CREATE FUNCTION lf_reserve_found(p_claim bigint,p_finder bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_claim lf_claims%ROWTYPE; v_listing lf_listings%ROWTYPE; v_transfer bigint;
BEGIN
    SELECT * INTO STRICT v_claim FROM lf_claims WHERE id=p_claim;
    SELECT * INTO STRICT v_listing FROM lf_listings WHERE id=v_claim.listing_id FOR UPDATE;
    SELECT * INTO STRICT v_claim FROM lf_claims WHERE id=p_claim FOR UPDATE;
    PERFORM lf_assert_user(p_finder);
    IF v_listing.author_id<>p_finder THEN RAISE EXCEPTION 'Только нашедший может согласовать возврат'; END IF;
    IF v_listing.custodian_org_id IS NOT NULL THEN RAISE EXCEPTION 'Выдачу вещи согласует организация-хранитель'; END IF;
    IF v_claim.state='accepted' THEN SELECT id INTO STRICT v_transfer FROM lf_transfers WHERE claim_id=p_claim; RETURN v_transfer; END IF;
    UPDATE lf_claims SET state='accepted' WHERE id=p_claim;
    UPDATE lf_listings SET state='reserved' WHERE id=v_listing.id;
    INSERT INTO lf_conversations(claim_id) VALUES (p_claim) ON CONFLICT DO NOTHING;
    INSERT INTO lf_transfers(claim_id) VALUES (p_claim) RETURNING id INTO v_transfer;
    INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('found.reserved',v_listing.id,jsonb_build_object('claim_id',p_claim));
    RETURN v_transfer;
END $$;
CREATE FUNCTION lf_confirm_transfer(p_transfer bigint,p_user bigint) RETURNS boolean
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_transfer lf_transfers%ROWTYPE; v_claim lf_claims%ROWTYPE; v_listing lf_listings%ROWTYPE;
BEGIN
    SELECT c.* INTO STRICT v_claim FROM lf_claims c JOIN lf_transfers t ON t.claim_id=c.id WHERE t.id=p_transfer;
    SELECT * INTO STRICT v_listing FROM lf_listings WHERE id=v_claim.listing_id FOR UPDATE;
    SELECT * INTO STRICT v_transfer FROM lf_transfers WHERE id=p_transfer FOR UPDATE;
    PERFORM lf_assert_user(p_user,false);
    IF p_user NOT IN (v_claim.claimant_id,v_listing.author_id) THEN RAISE EXCEPTION 'Нет доступа к передаче'; END IF;
    IF v_transfer.completed_at IS NOT NULL THEN RETURN true; END IF;
    IF v_claim.state<>'accepted' OR v_listing.state<>'reserved' THEN RAISE EXCEPTION 'Передача не согласована'; END IF;
    IF p_user=v_listing.author_id THEN v_transfer.finder_confirmed_at:=clock_timestamp(); ELSE v_transfer.owner_confirmed_at:=clock_timestamp(); END IF;
    IF v_transfer.finder_confirmed_at IS NOT NULL AND v_transfer.owner_confirmed_at IS NOT NULL THEN
      v_transfer.completed_at:=clock_timestamp();
      UPDATE lf_claims SET state='fulfilled' WHERE id=v_claim.id;
      UPDATE lf_listings SET state='returned' WHERE id=v_listing.id;
      INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('found.returned',v_listing.id,jsonb_build_object('claim_id',v_claim.id));
    END IF;
    UPDATE lf_transfers SET finder_confirmed_at=v_transfer.finder_confirmed_at,owner_confirmed_at=v_transfer.owner_confirmed_at,
      completed_at=v_transfer.completed_at WHERE id=p_transfer;
    RETURN v_transfer.completed_at IS NOT NULL;
END $$;
CREATE FUNCTION lf_message_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM lf_assert_user(NEW.sender_id,false);
    IF NOT EXISTS (SELECT 1 FROM lf_conversations d JOIN lf_claims c ON c.id=d.claim_id JOIN lf_listings l ON l.id=c.listing_id
      WHERE d.id=NEW.conversation_id AND NEW.sender_id IN (c.claimant_id,l.author_id)) THEN
      RAISE EXCEPTION 'Отправитель не участвует в диалоге';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_message_guard BEFORE INSERT OR UPDATE ON lf_messages FOR EACH ROW EXECUTE FUNCTION lf_message_guard();
CREATE FUNCTION lf_transfer_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF TG_OP='UPDATE' AND (NEW.claim_id<>OLD.claim_id OR OLD.completed_at IS NOT NULL) THEN
      RAISE EXCEPTION 'Завершённая передача и её участники неизменяемы';
    END IF;
    IF TG_OP='INSERT' AND (NEW.finder_confirmed_at IS NOT NULL OR NEW.owner_confirmed_at IS NOT NULL OR
      NOT EXISTS (SELECT 1 FROM lf_claims WHERE id=NEW.claim_id AND state='accepted')) THEN
      RAISE EXCEPTION 'Передача создаётся только после согласования заявки';
    END IF;
    IF NEW.moderator_id IS NOT NULL THEN
      PERFORM lf_assert_moderator(NEW.moderator_id);
      IF EXISTS (SELECT 1 FROM lf_claims c JOIN lf_listings l ON l.id=c.listing_id WHERE c.id=NEW.claim_id
        AND NEW.moderator_id IN (c.claimant_id,l.author_id)) THEN RAISE EXCEPTION 'Саморазрешение спора запрещено'; END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_transfer_guard BEFORE INSERT OR UPDATE ON lf_transfers FOR EACH ROW EXECUTE FUNCTION lf_transfer_guard();
CREATE TRIGGER lf_transfer_audit AFTER INSERT OR UPDATE ON lf_transfers FOR EACH ROW EXECUTE FUNCTION lf_audit_change();
CREATE FUNCTION lf_resolve_return(p_transfer bigint,p_moderator bigint,p_reason text) RETURNS void
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_claim lf_claims%ROWTYPE; v_transfer lf_transfers%ROWTYPE;
BEGIN
    SELECT c.* INTO STRICT v_claim FROM lf_claims c JOIN lf_transfers t ON t.claim_id=c.id WHERE t.id=p_transfer;
    PERFORM 1 FROM lf_listings WHERE id=v_claim.listing_id FOR UPDATE;
    SELECT * INTO STRICT v_transfer FROM lf_transfers WHERE id=p_transfer FOR UPDATE;
    PERFORM lf_assert_moderator(p_moderator);
    IF p_reason IS NULL OR length(trim(p_reason))=0 THEN RAISE EXCEPTION 'Требуется мотивированное решение'; END IF;
    IF v_transfer.completed_at IS NOT NULL OR v_claim.state<>'accepted' THEN RAISE EXCEPTION 'Передача уже закрыта или не согласована'; END IF;
    UPDATE lf_claims SET state='fulfilled' WHERE id=v_claim.id;
    UPDATE lf_listings SET state='returned' WHERE id=v_claim.listing_id;
    UPDATE lf_transfers SET moderator_id=p_moderator,override_reason=p_reason,completed_at=clock_timestamp() WHERE id=p_transfer;
END $$;

CREATE FUNCTION lf_permission_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_listing lf_listings%ROWTYPE;
BEGIN
    SELECT * INTO STRICT v_listing FROM lf_listings WHERE id=NEW.listing_id FOR UPDATE;
    IF TG_OP='INSERT' THEN PERFORM lf_assert_user(NEW.applicant_id); END IF;
    IF TG_OP='UPDATE' AND (NEW.listing_id,NEW.applicant_id,NEW.basis,NEW.evidence_object_key)
      IS DISTINCT FROM (OLD.listing_id,OLD.applicant_id,OLD.basis,OLD.evidence_object_key) THEN
      RAISE EXCEPTION 'Реквизиты заявки на продажу неизменяемы';
    END IF;
    IF NEW.applicant_id<>v_listing.author_id OR v_listing.kind<>'found' OR v_listing.custodian_org_id IS NOT NULL
      OR NOT EXISTS (SELECT 1 FROM lf_categories WHERE id=v_listing.category_id AND auction_allowed) THEN
      RAISE EXCEPTION 'Находка не допускается к продаже пользователем';
    END IF;
    IF TG_OP='UPDATE' AND OLD.state<>'pending' THEN RAISE EXCEPTION 'Рассмотренное основание неизменяемо'; END IF;
    IF NEW.state='approved' THEN
      PERFORM lf_assert_moderator(NEW.moderator_id);
      IF NEW.moderator_id=NEW.applicant_id THEN RAISE EXCEPTION 'Самосогласование запрещено'; END IF;
      IF v_listing.state<>'published' OR EXISTS (SELECT 1 FROM lf_claims WHERE listing_id=NEW.listing_id AND state IN ('pending','accepted')) THEN
        RAISE EXCEPTION 'Есть незавершённое обращение владельца';
      END IF;
      IF NEW.basis='six_months' AND (v_listing.official_reported_at IS NULL OR
        v_listing.official_reported_at+interval '6 months'>clock_timestamp()) THEN
        RAISE EXCEPTION 'Не истекло шесть месяцев после официального заявления';
      END IF;
    ELSIF NEW.state='rejected' THEN PERFORM lf_assert_moderator(NEW.moderator_id); END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_permission_guard BEFORE INSERT OR UPDATE ON lf_auction_permissions FOR EACH ROW EXECUTE FUNCTION lf_permission_guard();
CREATE FUNCTION lf_auction_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_listing lf_listings%ROWTYPE;
BEGIN
    SELECT * INTO STRICT v_listing FROM lf_listings WHERE id=NEW.listing_id FOR UPDATE;
    IF TG_OP='INSERT' THEN
      PERFORM lf_assert_user(NEW.seller_id);
      IF NEW.state NOT IN ('scheduled','active') THEN RAISE EXCEPTION 'Некорректный начальный статус аукциона'; END IF;
    ELSE
      IF (NEW.listing_id,NEW.seller_id,NEW.permission_id,NEW.starts_at,NEW.ends_at,NEW.start_price,NEW.bid_step)
        IS DISTINCT FROM (OLD.listing_id,OLD.seller_id,OLD.permission_id,OLD.starts_at,OLD.ends_at,OLD.start_price,OLD.bid_step) THEN
        RAISE EXCEPTION 'Условия аукциона зафиксированы';
      END IF;
      IF OLD.state IN ('finished','cancelled') THEN RAISE EXCEPTION 'Аукцион завершён'; END IF;
      IF NEW.state<>OLD.state AND NOT
        ((OLD.state='scheduled' AND NEW.state IN ('active','suspended','finished','cancelled')) OR
         (OLD.state='active' AND NEW.state IN ('suspended','finished','cancelled')) OR
         (OLD.state='suspended' AND NEW.state IN ('active','scheduled','cancelled'))) THEN
        RAISE EXCEPTION 'Недопустимый переход статуса аукциона';
      END IF;
    END IF;
    IF NEW.state IN ('scheduled','active') THEN
      IF v_listing.state<>'published' OR v_listing.custodian_org_id IS NOT NULL OR
         NOT EXISTS (SELECT 1 FROM lf_auction_permissions WHERE id=NEW.permission_id AND state='approved'
           AND reviewed_at>=v_listing.moderated_at AND (basis<>'six_months' OR
             (v_listing.official_reported_at IS NOT NULL AND v_listing.official_reported_at+interval '6 months'<=clock_timestamp()))) OR
         NOT EXISTS (SELECT 1 FROM lf_categories WHERE id=v_listing.category_id AND auction_allowed) OR
         EXISTS (SELECT 1 FROM lf_claims WHERE listing_id=NEW.listing_id AND state IN ('pending','accepted')) THEN
        RAISE EXCEPTION 'Аукцион не имеет действующего допуска';
      END IF;
      IF NEW.ends_at<=clock_timestamp() THEN RAISE EXCEPTION 'Аукцион уже просрочен'; END IF;
      IF NEW.state='active' AND NEW.starts_at>clock_timestamp() THEN RAISE EXCEPTION 'Аукцион ещё не начался'; END IF;
    END IF;
    IF NEW.state='finished' THEN
      IF OLD.state='suspended' OR NEW.ends_at>clock_timestamp() THEN RAISE EXCEPTION 'Аукцион нельзя завершить сейчас'; END IF;
      IF NEW.winner_bid_id IS DISTINCT FROM (SELECT id FROM lf_bids WHERE auction_id=NEW.id ORDER BY amount DESC,id LIMIT 1) THEN
        RAISE EXCEPTION 'Победитель должен иметь максимальную ставку';
      END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_auction_guard BEFORE INSERT OR UPDATE ON lf_auctions FOR EACH ROW EXECUTE FUNCTION lf_auction_guard();
CREATE FUNCTION lf_bid_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_auction lf_auctions%ROWTYPE; v_listing bigint; v_max numeric; v_now timestamptz;
BEGIN
    SELECT listing_id INTO STRICT v_listing FROM lf_auctions WHERE id=NEW.auction_id;
    PERFORM 1 FROM lf_listings WHERE id=v_listing FOR UPDATE;
    SELECT * INTO STRICT v_auction FROM lf_auctions WHERE id=NEW.auction_id FOR UPDATE;
    PERFORM lf_assert_user(NEW.bidder_id);
    v_now:=clock_timestamp();
    IF v_auction.state='scheduled' AND v_now>=v_auction.starts_at AND v_now<v_auction.ends_at THEN
      UPDATE lf_auctions SET state='active' WHERE id=NEW.auction_id;
      v_auction.state:='active'; v_now:=clock_timestamp();
    END IF;
    IF v_auction.state NOT IN ('scheduled','active') OR v_now<v_auction.starts_at OR v_now>=v_auction.ends_at THEN
      RAISE EXCEPTION 'Аукцион не принимает ставки';
    END IF;
    IF NEW.bidder_id=v_auction.seller_id THEN RAISE EXCEPTION 'Продавец не может делать ставки'; END IF;
    SELECT max(amount) INTO v_max FROM lf_bids WHERE auction_id=NEW.auction_id;
    IF NEW.amount<coalesce(v_max+v_auction.bid_step,v_auction.start_price) THEN RAISE EXCEPTION 'Ставка ниже допустимой'; END IF;
    NEW.placed_at:=v_now;
    RETURN NEW;
END $$;
CREATE TRIGGER lf_bid_guard BEFORE INSERT ON lf_bids FOR EACH ROW EXECUTE FUNCTION lf_bid_guard();
CREATE FUNCTION lf_place_bid(p_auction bigint,p_bidder bigint,p_amount numeric,p_key uuid) RETURNS bigint
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_listing bigint; v_bid lf_bids%ROWTYPE; v_id bigint;
BEGIN
    IF p_amount IS NULL OR p_amount<>round(p_amount,2) THEN RAISE EXCEPTION 'Сумма ставки должна иметь не более двух десятичных знаков'; END IF;
    SELECT listing_id INTO STRICT v_listing FROM lf_auctions WHERE id=p_auction;
    PERFORM 1 FROM lf_listings WHERE id=v_listing FOR UPDATE;
    PERFORM 1 FROM lf_auctions WHERE id=p_auction FOR UPDATE;
    SELECT * INTO v_bid FROM lf_bids WHERE request_key=p_key;
    IF FOUND THEN
      IF (v_bid.auction_id,v_bid.bidder_id,v_bid.amount) IS DISTINCT FROM (p_auction,p_bidder,p_amount) THEN
        RAISE EXCEPTION 'Ключ запроса использован для другой ставки';
      END IF;
      RETURN v_bid.id;
    END IF;
    INSERT INTO lf_bids(auction_id,bidder_id,amount,request_key) VALUES (p_auction,p_bidder,p_amount,p_key) RETURNING id INTO v_id;
    INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('auction.bid',p_auction,jsonb_build_object('bid_id',v_id));
    RETURN v_id;
END $$;
CREATE FUNCTION lf_finalize_auction(p_auction bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_auction lf_auctions%ROWTYPE; v_listing bigint; v_winner bigint;
BEGIN
    SELECT listing_id INTO STRICT v_listing FROM lf_auctions WHERE id=p_auction;
    PERFORM 1 FROM lf_listings WHERE id=v_listing FOR UPDATE;
    SELECT * INTO STRICT v_auction FROM lf_auctions WHERE id=p_auction FOR UPDATE;
    IF v_auction.state='finished' THEN RETURN v_auction.winner_bid_id; END IF;
    IF v_auction.state NOT IN ('active','scheduled') OR v_auction.ends_at>clock_timestamp() THEN
      RAISE EXCEPTION 'Аукцион ещё не завершён или приостановлен';
    END IF;
    SELECT id INTO v_winner FROM lf_bids WHERE auction_id=p_auction ORDER BY amount DESC,id LIMIT 1;
    UPDATE lf_auctions SET state='finished',winner_bid_id=v_winner,closed_at=clock_timestamp() WHERE id=p_auction;
    IF v_winner IS NOT NULL THEN UPDATE lf_listings SET state='auctioned' WHERE id=v_listing; END IF;
    INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('auction.finished',p_auction,jsonb_build_object('winner_bid_id',v_winner));
    RETURN v_winner;
END $$;
CREATE PROCEDURE lf_close_due_auctions(p_limit integer DEFAULT 100)
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_id bigint;
BEGIN
    IF p_limit NOT BETWEEN 1 AND 1000 THEN RAISE EXCEPTION 'Некорректный размер пакета'; END IF;
    FOR v_id IN SELECT id FROM lf_auctions WHERE state IN ('scheduled','active') AND ends_at<=clock_timestamp()
      ORDER BY ends_at,id LIMIT p_limit LOOP
      PERFORM lf_finalize_auction(v_id);
    END LOOP;
END $$;
CREATE FUNCTION lf_search_listings(p_text text DEFAULT NULL,p_kind varchar DEFAULT NULL,p_category bigint DEFAULT NULL,
    p_location bigint DEFAULT NULL,p_from timestamptz DEFAULT NULL,p_to timestamptz DEFAULT NULL,
    p_before bigint DEFAULT NULL,p_limit integer DEFAULT 20)
RETURNS TABLE(id bigint,title varchar,kind varchar,event_at timestamptz,location_id bigint)
LANGUAGE plpgsql STABLE SET search_path FROM CURRENT AS $$
BEGIN
    IF p_limit NOT BETWEEN 1 AND 100 OR (p_from IS NOT NULL AND p_to IS NOT NULL AND p_from>p_to) THEN
      RAISE EXCEPTION 'Некорректные параметры поиска';
    END IF;
    RETURN QUERY SELECT l.id,l.title,l.kind,l.event_at,l.location_id FROM lf_listings l
      WHERE l.state='published' AND (p_text IS NULL OR l.search_vector @@ plainto_tsquery('russian',p_text))
        AND (p_kind IS NULL OR l.kind=p_kind) AND (p_category IS NULL OR l.category_id=p_category)
        AND (p_location IS NULL OR l.location_id=p_location) AND (p_from IS NULL OR l.event_at>=p_from)
        AND (p_to IS NULL OR l.event_at<=p_to) AND (p_before IS NULL OR l.id<p_before)
      ORDER BY l.id DESC LIMIT p_limit;
END $$;
-- Без закрытых признаков и персональных реквизитов аккаунта.
CREATE VIEW lf_public_listings AS SELECT l.id,l.kind,l.title,l.description,l.event_at,l.event_until,l.state,c.name AS category,
    p.city,p.description AS place,p.metro_station,o.name AS custodian,o.phone,o.instructions,o.source_url,o.verified_on
    FROM lf_listings l JOIN lf_categories c ON c.id=l.category_id JOIN lf_locations p ON p.id=l.location_id
    LEFT JOIN lf_organizations o ON o.id=coalesce(l.custodian_org_id,p.organization_id)
    WHERE l.state IN ('published','reserved','returned','auctioned');
