\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
BEGIN;
SET LOCAL search_path TO :"schema", pg_catalog;
INSERT INTO lf_roles VALUES ('user','Пользователь'),('moderator','Модератор'),('admin','Администратор');
-- Синтетические аккаунты: домен .invalid, не реальные адреса и не рабочие пароли.
INSERT INTO lf_users(id,email,display_name,password_hash,state,email_confirmed_at) VALUES
 (1,'moderator@example.invalid','Модератор','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','verified',clock_timestamp()),
 (2,'admin@example.invalid','Администратор','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','verified',clock_timestamp()),
 (3,'finder@example.invalid','Нашедший','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','verified',clock_timestamp()),
 (4,'owner@example.invalid','Владелец','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','verified',clock_timestamp()),
 (5,'buyer@example.invalid','Покупатель','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','verified',clock_timestamp()),
 (6,'pending@example.invalid','Ожидает проверки','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','pending',clock_timestamp()),
 (7,'blocked@example.invalid','Заблокирован','$2b$12$W6ySVS7b1LxOvHQsdCyleuvt0an0BnLaTgoOA/D.r0A//b/MeczU6','blocked',clock_timestamp());
INSERT INTO lf_user_roles VALUES (1,'user'),(1,'moderator'),(2,'user'),(2,'admin'),(3,'user'),(4,'user'),(5,'user'),(6,'user'),(7,'user');
INSERT INTO lf_verifications(user_id) VALUES (6);
INSERT INTO lf_verification_tokens(user_id,purpose,token_hash,expires_at) VALUES (6,'email_confirm',repeat('b',64),clock_timestamp()+interval '24 hours');
INSERT INTO lf_refresh_tokens(user_id,token_hash,expires_at) VALUES (4,repeat('a',64),clock_timestamp()+interval '7 days');
INSERT INTO lf_tariffs(id,title,amount,duration_days) VALUES (1,'Учебный тариф на 30 суток',149.00,30);
INSERT INTO lf_payment_orders(id,user_id,tariff_id,request_key,amount,duration_days) VALUES
 (1,3,1,'10000000-0000-0000-0000-000000000001',149,30),
 (2,4,1,'10000000-0000-0000-0000-000000000002',149,30),
 (3,5,1,'10000000-0000-0000-0000-000000000003',149,30);
SELECT lf_activate_subscription(1,'demo-payment-1',149);
SELECT lf_activate_subscription(2,'demo-payment-2',149);
SELECT lf_activate_subscription(3,'demo-payment-3',149);
INSERT INTO lf_organizations(id,name,phone,address,instructions,source_url,verified_on) VALUES
 (1,'Петербургский метрополитен','8-800-350-11-55',NULL,
 'Уточнить сведения о забытых вещах в информационно-справочном центре. В метрополитене вещи хранятся 10 дней, затем передаются в городской центр утерянных документов и средств связи на Большой Монетной улице, 16. Для получения нужны документ, удостоверяющий личность, и заявление.',
 'https://metro.spb.ru/zabitieveshy.html?v=1','2026-09-27');
INSERT INTO lf_locations(id,description,latitude,longitude,metro_station,organization_id) VALUES
 (1,'У входа в Таврический сад',59.947500,30.376700,NULL,NULL),
 (2,'Вестибюль станции метро «Чернышевская»',59.944600,30.359800,'Чернышевская',1);
INSERT INTO lf_categories(id,name,auction_allowed) VALUES
 (1,'Аксессуары',true),(2,'Электроника',true),(3,'Документы и банковские карты',false),(4,'Сумки',true);
INSERT INTO lf_listings(id,author_id,category_id,location_id,kind,title,description,event_at,state,moderator_id,moderated_at,official_reported_at,custodian_org_id) VALUES
 (1,3,1,1,'found','Синий зонт','Складной зонт найден у входа в сад.',clock_timestamp()-interval '2 days','published',1,clock_timestamp(),NULL,NULL),
 (2,3,2,1,'found','Беспроводные наушники','Наушники в зарядном футляре. Особые приметы сообщаются в заявке.',clock_timestamp()-interval '8 months','published',1,clock_timestamp(),clock_timestamp()-interval '7 months',NULL),
 (3,3,4,2,'found','Сумка в метро','Сумка передана сотруднику станции.',clock_timestamp()-interval '1 day','published',1,clock_timestamp(),NULL,1),
 (4,3,3,1,'found','Обложка с документом','Данные документа скрыты. Возврат после проверки признаков.',clock_timestamp()-interval '3 days','published',1,clock_timestamp(),NULL,NULL),
 (5,4,1,1,'lost','Связка ключей','Потеряна связка ключей в районе сада.',clock_timestamp()-interval '4 days','published',1,clock_timestamp(),NULL,NULL),
 (6,3,4,1,'found','Спортивная сумка','Сумка без документов. Подано официальное заявление.',clock_timestamp()-interval '8 months','published',1,clock_timestamp(),clock_timestamp()-interval '7 months',NULL),
 (7,3,1,1,'found','Шарф','Шарф с вышивкой.',clock_timestamp()-interval '5 days','published',1,clock_timestamp(),NULL,NULL);
INSERT INTO lf_listing_images(listing_id,object_key,media_type,size_bytes,position) VALUES
 (1,'demo/listings/1/umbrella.jpg','image/jpeg',102400,1);
INSERT INTO lf_private_attributes(listing_id,name,value) VALUES (1,'Надпись на ручке','Тестовая надпись'),(7,'Цвет вышивки','Зелёный');
INSERT INTO lf_claims(id,listing_id,claimant_id,evidence) VALUES
 (1,1,4,'На ручке есть тестовая надпись.'),(2,1,5,'Мой зонт пропал в саду.'),(3,7,4,'На шарфе зелёная вышивка.');
INSERT INTO lf_conversations(claim_id) VALUES (1);
INSERT INTO lf_messages(conversation_id,sender_id,body) VALUES (1,3,'Уточните время, когда вы оставили зонт.');
SELECT lf_reserve_found(3,3);
SELECT lf_confirm_transfer(1,3);
SELECT lf_confirm_transfer(1,4);
INSERT INTO lf_complaints(reporter_id,listing_id,reason) VALUES (5,5,'Проверить возможный дубликат объявления.');
INSERT INTO lf_auction_permissions(id,listing_id,applicant_id,basis,evidence_object_key,state,moderator_id,reviewed_at) VALUES
 (1,2,3,'six_months','demo/permissions/1.pdf','approved',1,clock_timestamp()),
 (2,6,3,'six_months','demo/permissions/2.pdf','pending',NULL,NULL);
INSERT INTO lf_auctions(id,listing_id,seller_id,permission_id,starts_at,ends_at,start_price,bid_step,state) VALUES
 (1,2,3,1,clock_timestamp()-interval '1 minute',clock_timestamp()+interval '1 day',1000,100,'active');
SELECT lf_place_bid(1,4,1000,'20000000-0000-0000-0000-000000000001');
SELECT lf_place_bid(1,5,1100,'20000000-0000-0000-0000-000000000002');
-- Явные идентификаторы нужны для воспроизводимой демонстрации. Синхронизируем последовательности.
DO $$ DECLARE r record; BEGIN
 FOR r IN SELECT table_name FROM information_schema.columns WHERE table_schema=current_schema()
   AND column_name='id' AND table_name LIKE 'lf\_%' ESCAPE '\' AND is_identity='YES' LOOP
   EXECUTE format('SELECT setval(pg_get_serial_sequence(%L,%L),coalesce(max(id),1),max(id) IS NOT NULL) FROM %I',r.table_name,'id',r.table_name);
 END LOOP;
END $$;
ANALYZE lf_listings; ANALYZE lf_bids; ANALYZE lf_subscriptions;
COMMIT;
