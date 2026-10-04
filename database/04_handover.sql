-- Повторяемое обновление только объектов курсовой. Вызывается внутри транзакции.
DO $$ BEGIN
 IF obj_description('lf_users'::regclass,'pg_class') IS DISTINCT FROM 'poteryashki.course.v1' THEN
   RAISE EXCEPTION 'Отсутствует маркер курсовой; обновление запрещено';
 END IF;
END $$;
ALTER TABLE lf_claims ADD COLUMN IF NOT EXISTS auction_id bigint;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='lf_claims'::regclass AND conname='lf_claim_auction_fk') THEN
   ALTER TABLE lf_claims ADD CONSTRAINT lf_claim_auction_fk FOREIGN KEY (auction_id) REFERENCES lf_auctions;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='lf_claims'::regclass AND conname='lf_claim_auction_unique') THEN
   ALTER TABLE lf_claims ADD CONSTRAINT lf_claim_auction_unique UNIQUE (auction_id);
 END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS lf_expiry_event_once ON lf_outbox_events(kind,aggregate_id) WHERE kind='subscription.expired';

CREATE OR REPLACE FUNCTION lf_claim_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_listing lf_listings%ROWTYPE;
BEGIN
 SELECT * INTO STRICT v_listing FROM lf_listings WHERE id=NEW.listing_id FOR UPDATE;
 IF TG_OP='INSERT' THEN
   IF NEW.auction_id IS NULL THEN
     PERFORM lf_assert_user(NEW.claimant_id);
     IF NEW.state<>'pending' OR v_listing.kind<>'found' OR v_listing.state<>'published' OR NEW.claimant_id=v_listing.author_id THEN
       RAISE EXCEPTION 'Нельзя подать заявку на эту находку';
     END IF;
     UPDATE lf_auctions SET state='suspended' WHERE listing_id=NEW.listing_id AND state IN ('scheduled','active');
   ELSE
     -- Результат торгов не зависит от истечения подписки победителя после ставки.
     IF NEW.state<>'accepted' OR v_listing.state<>'auctioned' OR NOT EXISTS (
       SELECT 1 FROM lf_auctions a JOIN lf_bids b ON b.id=a.winner_bid_id
       WHERE a.id=NEW.auction_id AND a.listing_id=NEW.listing_id AND a.state='finished'
         AND a.seller_id=v_listing.author_id AND b.bidder_id=NEW.claimant_id
     ) THEN RAISE EXCEPTION 'Передача разрешена только победителю завершённых торгов'; END IF;
   END IF;
 ELSE
   IF (NEW.listing_id,NEW.claimant_id,NEW.auction_id) IS DISTINCT FROM (OLD.listing_id,OLD.claimant_id,OLD.auction_id) THEN
     RAISE EXCEPTION 'Участники и основание заявки неизменяемы';
   END IF;
   IF NEW.state<>OLD.state AND NOT
     ((OLD.state='pending' AND NEW.state IN ('accepted','rejected','cancelled')) OR
      (OLD.state='accepted' AND NEW.state IN ('fulfilled','cancelled'))) THEN
     RAISE EXCEPTION 'Недопустимый переход статуса заявки';
   END IF;
   IF NEW.state='accepted' AND OLD.state<>'accepted' AND
     (v_listing.state<>'published' OR EXISTS (SELECT 1 FROM lf_auctions WHERE listing_id=NEW.listing_id AND state IN ('scheduled','active','suspended'))) THEN
     RAISE EXCEPTION 'Находка недоступна для резервирования';
   END IF;
   IF NEW.state='cancelled' AND OLD.state='accepted' AND
     (OLD.auction_id IS NOT NULL OR EXISTS (SELECT 1 FROM lf_transfers WHERE claim_id=OLD.id AND
       (finder_confirmed_at IS NOT NULL OR owner_confirmed_at IS NOT NULL OR completed_at IS NOT NULL))) THEN
     RAISE EXCEPTION 'Подтверждённую передачу или результат торгов разбирает модератор';
   END IF;
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION lf_message_guard() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 PERFORM lf_assert_user(NEW.sender_id,false);
 IF NOT EXISTS (SELECT 1 FROM lf_conversations d JOIN lf_claims c ON c.id=d.claim_id JOIN lf_listings l ON l.id=c.listing_id
   WHERE d.id=NEW.conversation_id AND (NEW.sender_id IN(c.claimant_id,l.author_id) OR
     (EXISTS (SELECT 1 FROM lf_user_roles WHERE user_id=NEW.sender_id AND role_code IN('moderator','admin')) AND
      EXISTS (SELECT 1 FROM lf_complaints WHERE listing_id=l.id AND state='pending')))) THEN
   RAISE EXCEPTION 'Отправитель не участвует в диалоге или открытом разборе';
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION lf_prepare_auction_handover(p_auction bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_auction lf_auctions%ROWTYPE; v_listing bigint; v_claim bigint; v_bidder bigint;
BEGIN
 SELECT listing_id INTO STRICT v_listing FROM lf_auctions WHERE id=p_auction;
 PERFORM 1 FROM lf_listings WHERE id=v_listing FOR UPDATE;
 SELECT * INTO STRICT v_auction FROM lf_auctions WHERE id=p_auction FOR UPDATE;
 IF v_auction.state<>'finished' THEN RAISE EXCEPTION 'Торги ещё не завершены'; END IF;
 IF v_auction.winner_bid_id IS NULL THEN RETURN NULL; END IF;
 SELECT id INTO v_claim FROM lf_claims WHERE auction_id=p_auction;
 IF v_claim IS NOT NULL THEN RETURN v_claim; END IF;
 SELECT bidder_id INTO STRICT v_bidder FROM lf_bids WHERE id=v_auction.winner_bid_id;
 INSERT INTO lf_claims(listing_id,claimant_id,auction_id,evidence,state)
   VALUES (v_listing,v_bidder,p_auction,'Передача победителю аукциона №'||p_auction,'accepted') RETURNING id INTO v_claim;
 INSERT INTO lf_conversations(claim_id) VALUES (v_claim);
 INSERT INTO lf_transfers(claim_id) VALUES (v_claim);
 UPDATE lf_listings SET state='reserved' WHERE id=v_listing;
 INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('found.reserved',v_listing,jsonb_build_object('claim_id',v_claim,'auction_id',p_auction));
 RETURN v_claim;
END $$;

CREATE OR REPLACE FUNCTION lf_finalize_auction(p_auction bigint) RETURNS bigint
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE v_auction lf_auctions%ROWTYPE; v_listing bigint; v_winner bigint;
BEGIN
 SELECT listing_id INTO STRICT v_listing FROM lf_auctions WHERE id=p_auction;
 PERFORM 1 FROM lf_listings WHERE id=v_listing FOR UPDATE;
 SELECT * INTO STRICT v_auction FROM lf_auctions WHERE id=p_auction FOR UPDATE;
 IF v_auction.state='finished' THEN
   PERFORM lf_prepare_auction_handover(p_auction); RETURN v_auction.winner_bid_id;
 END IF;
 IF v_auction.state NOT IN ('active','scheduled') OR v_auction.ends_at>clock_timestamp() THEN
   RAISE EXCEPTION 'Аукцион ещё не завершён или приостановлен';
 END IF;
 SELECT id INTO v_winner FROM lf_bids WHERE auction_id=p_auction ORDER BY amount DESC,id LIMIT 1;
 UPDATE lf_auctions SET state='finished',winner_bid_id=v_winner,closed_at=clock_timestamp() WHERE id=p_auction;
 IF v_winner IS NOT NULL THEN UPDATE lf_listings SET state='auctioned' WHERE id=v_listing; END IF;
 PERFORM lf_prepare_auction_handover(p_auction);
 INSERT INTO lf_outbox_events(kind,aggregate_id,payload) VALUES ('auction.finished',p_auction,jsonb_build_object('winner_bid_id',v_winner));
 RETURN v_winner;
END $$;
