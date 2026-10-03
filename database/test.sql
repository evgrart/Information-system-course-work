\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
BEGIN;
SET LOCAL search_path TO :"schema", pg_catalog;
CREATE TEMP TABLE lf_test_results(name text PRIMARY KEY,result text NOT NULL);
CREATE FUNCTION pg_temp.expect_error(p_name text,p_sql text,p_state text DEFAULT 'P0001') RETURNS void
LANGUAGE plpgsql AS $$
DECLARE v_state text;
BEGIN
 BEGIN EXECUTE p_sql;
 EXCEPTION WHEN OTHERS THEN
  GET STACKED DIAGNOSTICS v_state=RETURNED_SQLSTATE;
  IF v_state<>p_state THEN RAISE EXCEPTION '%: ожидался SQLSTATE %, получен % (%)',p_name,p_state,v_state,SQLERRM; END IF;
  INSERT INTO lf_test_results VALUES (p_name,'PASS'); RETURN;
 END;
 RAISE EXCEPTION '%: операция ошибочно разрешена',p_name USING ERRCODE='XX000';
END $$;
SELECT pg_temp.expect_error('01. Неподтверждённая почта',$q$INSERT INTO lf_users(email,display_name,password_hash,state) VALUES ('invalid@example.invalid','Тест','test','verified')$q$,'23514');
SELECT pg_temp.expect_error('02. Повтор почты',$q$INSERT INTO lf_users(email,display_name,password_hash) VALUES ('finder@example.invalid','Тест','test')$q$,'23505');
SELECT pg_temp.expect_error('03. Роль вне справочника',$q$INSERT INTO lf_user_roles VALUES (3,'unknown')$q$,'23503');
SELECT pg_temp.expect_error('04. Не модератор',$q$SELECT lf_assert_moderator(3)$q$);
SELECT pg_temp.expect_error('05. Заблокированный профиль',$q$SELECT lf_assert_user(7)$q$);
SELECT pg_temp.expect_error('06. Ожидающий проверки',$q$SELECT lf_assert_user(6)$q$);
SELECT pg_temp.expect_error('07. Самопроверка профиля',$q$UPDATE lf_verifications SET state='approved',reviewer_id=6,reviewed_at=clock_timestamp() WHERE user_id=6$q$);
SELECT pg_temp.expect_error('08. Подмена суммы заказа',$q$INSERT INTO lf_payment_orders(user_id,tariff_id,request_key,amount,duration_days) VALUES (3,1,'30000000-0000-0000-0000-000000000001',1,30)$q$);
SELECT pg_temp.expect_error('09. Неверная сумма callback',$q$SELECT lf_activate_subscription(1,'demo-payment-1',1)$q$);
SELECT pg_temp.expect_error('10. Повтор события для другого заказа',$q$SELECT lf_activate_subscription(2,'demo-payment-1',149)$q$);
SELECT pg_temp.expect_error('11. Изменение истории события',$q$UPDATE lf_payment_events SET amount=1 WHERE provider_event_id='demo-payment-1'$q$);
DO $$ DECLARE v_count integer; v_id bigint; BEGIN
 SELECT count(*) INTO v_count FROM lf_subscriptions;
 SELECT lf_activate_subscription(1,'demo-payment-1',149) INTO v_id;
 IF (SELECT count(*) FROM lf_subscriptions)<>v_count THEN RAISE EXCEPTION 'Повтор оплаты продлил подписку'; END IF;
 INSERT INTO lf_test_results VALUES ('12. Идемпотентность оплаты','PASS');
 INSERT INTO lf_payment_orders(id,user_id,tariff_id,request_key,amount,duration_days)
 VALUES (20,3,1,'30000000-0000-0000-0000-000000000020',149,30);
 PERFORM lf_activate_subscription(20,'test-renewal',149);
 IF (SELECT starts_at FROM lf_subscriptions WHERE payment_order_id=20)<>(SELECT ends_at FROM lf_subscriptions WHERE payment_order_id=1) THEN
  RAISE EXCEPTION 'Продление потеряло оплаченный остаток'; END IF;
 INSERT INTO lf_test_results VALUES ('13. Продление от конца текущего периода','PASS');
END $$;
SELECT pg_temp.expect_error('14. Пересечение подписок',$q$INSERT INTO lf_subscriptions(user_id,payment_order_id,starts_at,ends_at) SELECT user_id,payment_order_id,starts_at,ends_at FROM lf_subscriptions WHERE payment_order_id=20$q$);
SELECT pg_temp.expect_error('15. Будущее время события',$q$UPDATE lf_listings SET event_at=clock_timestamp()+interval '1 day' WHERE id=1$q$);
SELECT pg_temp.expect_error('16. Файл больше 5 МиБ',$q$INSERT INTO lf_listing_images(listing_id,object_key,media_type,size_bytes,position) VALUES (1,'test/huge.jpg','image/jpeg',5242881,2)$q$,'23514');
SELECT pg_temp.expect_error('17. Собственная заявка',$q$INSERT INTO lf_claims(listing_id,claimant_id,evidence) VALUES (1,3,'Моя вещь')$q$);
SELECT pg_temp.expect_error('18. Заявка на потерю',$q$INSERT INTO lf_claims(listing_id,claimant_id,evidence) VALUES (5,5,'Моя вещь')$q$);
SELECT pg_temp.expect_error('19. Дубликат открытой заявки',$q$INSERT INTO lf_claims(listing_id,claimant_id,evidence) VALUES (1,4,'Повтор')$q$,'23505');
SELECT pg_temp.expect_error('20. Чужой диалог',$q$INSERT INTO lf_messages(conversation_id,sender_id,body) VALUES (1,5,'Чужое сообщение')$q$);
SELECT pg_temp.expect_error('21. Продажа документа',$q$INSERT INTO lf_auction_permissions(listing_id,applicant_id,basis,evidence_object_key) VALUES (4,3,'other_title','test/document.pdf')$q$);
SELECT pg_temp.expect_error('22. Продажа вещи организации',$q$INSERT INTO lf_auction_permissions(listing_id,applicant_id,basis,evidence_object_key) VALUES (3,3,'other_title','test/metro.pdf')$q$);
SELECT pg_temp.expect_error('23. Нет официального шестимесячного срока',$q$INSERT INTO lf_auction_permissions(listing_id,applicant_id,basis,evidence_object_key,state,moderator_id,reviewed_at) VALUES (1,3,'six_months','test/early.pdf','approved',1,clock_timestamp())$q$);
SELECT pg_temp.expect_error('24. Продавец делает ставку',$q$SELECT lf_place_bid(1,3,1200,'30000000-0000-0000-0000-000000000024')$q$);
SELECT pg_temp.expect_error('25. Недостаточный шаг ставки',$q$SELECT lf_place_bid(1,4,1150,'30000000-0000-0000-0000-000000000025')$q$);
SELECT pg_temp.expect_error('26. Повтор ключа с другой суммой',$q$SELECT lf_place_bid(1,4,1200,'20000000-0000-0000-0000-000000000001')$q$);
SELECT pg_temp.expect_error('27. Второй незавершённый аукцион',$q$INSERT INTO lf_auctions(listing_id,seller_id,permission_id,starts_at,ends_at,start_price,bid_step,state) VALUES (2,3,1,clock_timestamp(),clock_timestamp()+interval '1 hour',1000,100,'active')$q$,'23505');
SELECT pg_temp.expect_error('28. Преждевременное завершение',$q$SELECT lf_finalize_auction(1)$q$);
SELECT pg_temp.expect_error('29. Изменение ставки',$q$UPDATE lf_bids SET amount=9000 WHERE id=1$q$);
SELECT pg_temp.expect_error('30. Редактирование аудита',$q$DELETE FROM lf_audit_entries$q$);
DO $$ DECLARE v_id bigint; BEGIN
 v_id:=lf_place_bid(1,4,1200,'30000000-0000-0000-0000-000000000031');
 IF lf_place_bid(1,4,1200,'30000000-0000-0000-0000-000000000031')<>v_id THEN RAISE EXCEPTION 'Повтор ставки создал новый результат'; END IF;
 INSERT INTO lf_test_results VALUES ('31. Идемпотентность ставки','PASS');
 INSERT INTO lf_claims(listing_id,claimant_id,evidence) VALUES (2,4,'Особый признак наушников');
 IF (SELECT state FROM lf_auctions WHERE id=1)<>'suspended' THEN RAISE EXCEPTION 'Аукцион не приостановлен'; END IF;
 INSERT INTO lf_test_results VALUES ('32. Обращение владельца приостанавливает торги','PASS');
END $$;
SELECT pg_temp.expect_error('33. Ставка после обращения владельца',$q$SELECT lf_place_bid(1,5,1300,'30000000-0000-0000-0000-000000000033')$q$);
DO $$ DECLARE v_transfer bigint; BEGIN
 v_transfer:=lf_reserve_found(1,3);
 IF (SELECT state FROM lf_listings WHERE id=1)<>'reserved' THEN RAISE EXCEPTION 'Резерв не создан'; END IF;
 INSERT INTO lf_test_results VALUES ('34. Согласование возврата','PASS');
 PERFORM pg_temp.expect_error('35. Второй резерв',$q$SELECT lf_reserve_found(2,3)$q$);
 PERFORM pg_temp.expect_error('36. Чужое подтверждение',format('SELECT lf_confirm_transfer(%s,5)',v_transfer));
 -- Истёкшая подписка не мешает закончить уже согласованный возврат.
 UPDATE lf_subscriptions SET starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' WHERE user_id=4;
 PERFORM pg_temp.expect_error('37. Новая операция после окончания подписки',$q$SELECT lf_assert_user(4)$q$);
 IF lf_confirm_transfer(v_transfer,3) THEN RAISE EXCEPTION 'Одного подтверждения недостаточно'; END IF;
 INSERT INTO lf_test_results VALUES ('38. Одно подтверждение не завершает возврат','PASS');
 IF NOT lf_confirm_transfer(v_transfer,4) THEN RAISE EXCEPTION 'Возврат не завершён'; END IF;
 IF (SELECT state FROM lf_listings WHERE id=1)<>'returned' THEN RAISE EXCEPTION 'Статус возврата не зафиксирован'; END IF;
 INSERT INTO lf_test_results VALUES ('39. Два подтверждения, включая истёкшую подписку','PASS');
END $$;
SET CONSTRAINTS ALL IMMEDIATE;
SELECT pg_temp.expect_error('40. Прямой резерв без принятой заявки',$q$UPDATE lf_listings SET state='reserved' WHERE id=3$q$);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='lf_public_listings' AND column_name IN ('password_hash','value','email')) THEN
   RAISE EXCEPTION 'В публичном представлении раскрыты закрытые данные'; END IF;
 IF NOT EXISTS (SELECT 1 FROM lf_search_listings('сумка')) THEN RAISE EXCEPTION 'Поиск не находит сумму'; END IF;
 INSERT INTO lf_test_results VALUES ('41. Публичное представление и русский поиск','PASS');
END $$;
SELECT pg_temp.expect_error('42. Некорректный интервал потери',$q$INSERT INTO lf_listings(author_id,category_id,location_id,kind,title,description,event_at,event_until) VALUES (3,1,1,'lost','Тест','Интервал',clock_timestamp()-interval '1 day',clock_timestamp()-interval '2 days')$q$,'23514');
SELECT pg_temp.expect_error('43. Прямой возврат без передачи',$q$UPDATE lf_listings SET state='returned' WHERE id=3$q$);
SELECT pg_temp.expect_error('44. Прямая продажа без победителя',$q$UPDATE lf_listings SET state='auctioned' WHERE id=3$q$);
SET CONSTRAINTS ALL DEFERRED;
DO $$ DECLARE v_claim bigint; v_transfer bigint; BEGIN
 UPDATE lf_claims SET state='rejected' WHERE listing_id=2 AND state='pending';
 UPDATE lf_auctions SET state='active' WHERE id=1;
 UPDATE lf_users SET state='blocked' WHERE id=5;
 IF (SELECT state FROM lf_auctions WHERE id=1)<>'suspended' THEN RAISE EXCEPTION 'Блокировка участника не приостановила торги'; END IF;
 INSERT INTO lf_test_results VALUES ('45. Блокировка участника приостанавливает торги','PASS');
 UPDATE lf_subscriptions SET starts_at=starts_at+interval '60 days',ends_at=ends_at+interval '60 days' WHERE user_id=4;
 INSERT INTO lf_claims(listing_id,claimant_id,evidence) VALUES (4,4,'Закрытый признак документа') RETURNING id INTO v_claim;
 v_transfer:=lf_reserve_found(v_claim,3);
 PERFORM pg_temp.expect_error('46. Разрешение спора без роли',format('SELECT lf_resolve_return(%s,3,%L)',v_transfer,'Решение'));
 PERFORM lf_resolve_return(v_transfer,1,'Получены подтверждения сторон в обращении к модератору.');
 IF (SELECT completed_at IS NOT NULL FROM lf_transfers WHERE id=v_transfer) IS NOT TRUE THEN RAISE EXCEPTION 'Решение не завершило возврат'; END IF;
 INSERT INTO lf_test_results VALUES ('47. Мотивированное решение модератора','PASS');
 IF lf_consume_token(repeat('b',64),'email_confirm')<>6 THEN RAISE EXCEPTION 'Ошибка подтверждения почты'; END IF;
 INSERT INTO lf_test_results VALUES ('48. Одноразовый токен подтверждения','PASS');
END $$;
SELECT pg_temp.expect_error('49. Повтор одноразового токена',$q$SELECT lf_consume_token(repeat('b',64),'email_confirm')$q$);
SELECT pg_temp.expect_error('50. Изменение завершённой передачи',$q$UPDATE lf_transfers SET owner_confirmed_at=NULL WHERE id=1$q$);
SET CONSTRAINTS ALL IMMEDIATE;
SELECT * FROM lf_test_results ORDER BY name;
SELECT count(*) AS passed FROM lf_test_results;
ROLLBACK;
-- Проверки не изменяют учебный набор; последовательности могут получить пропуски.
