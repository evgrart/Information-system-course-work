### 8.2. Словарь отношений

#### 1. Допуск к продаже: lf_auction_permissions

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| listing_id | bigint | Нет | FK |
| applicant_id | bigint | Нет | FK |
| basis | varchar(20) | Нет | — |
| evidence_object_key | varchar(240) | Нет | — |
| state | varchar(12) | Нет | — |
| moderator_id | bigint | Да | FK |
| reviewed_at | timestamptz | Да | — |
| reason | text | Да | — |
| created_at | timestamptz | Нет | — |

FOREIGN KEY (applicant_id) REFERENCES lf_users(id).

UNIQUE (id, listing_id, applicant_id).

FOREIGN KEY (listing_id) REFERENCES lf_listings(id).

FOREIGN KEY (moderator_id) REFERENCES lf_users(id).

PRIMARY KEY (id).

#### 2. Аукцион: lf_auctions

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK, FK |
| listing_id | bigint | Нет | FK |
| seller_id | bigint | Нет | FK |
| permission_id | bigint | Нет | FK |
| starts_at | timestamptz | Нет | — |
| ends_at | timestamptz | Нет | — |
| start_price | numeric(12,2) | Нет | — |
| bid_step | numeric(12,2) | Нет | — |
| state | varchar(12) | Нет | — |
| winner_bid_id | bigint | Да | FK |
| closed_at | timestamptz | Да | — |

FOREIGN KEY (listing_id) REFERENCES lf_listings(id).

FOREIGN KEY (permission_id, listing_id, seller_id) REFERENCES lf_auction_permissions(id, listing_id, applicant_id).

PRIMARY KEY (id).

FOREIGN KEY (seller_id) REFERENCES lf_users(id).

FOREIGN KEY (id, winner_bid_id) REFERENCES lf_bids(auction_id, id) DEFERRABLE INITIALLY DEFERRED.

#### 3. Запись аудита: lf_audit_entries

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| actor_id | bigint | Да | FK |
| action | varchar(80) | Нет | — |
| entity_table | varchar(80) | Нет | — |
| entity_id | bigint | Нет | — |
| details | jsonb | Нет | — |
| created_at | timestamptz | Нет | — |

FOREIGN KEY (actor_id) REFERENCES lf_users(id).

PRIMARY KEY (id).

#### 4. Ставка: lf_bids

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| auction_id | bigint | Нет | FK |
| bidder_id | bigint | Нет | FK |
| request_key | uuid | Нет | — |
| amount | numeric(12,2) | Нет | — |
| placed_at | timestamptz | Нет | — |

FOREIGN KEY (auction_id) REFERENCES lf_auctions(id).

UNIQUE (auction_id, id).

FOREIGN KEY (bidder_id) REFERENCES lf_users(id).

PRIMARY KEY (id).

UNIQUE (request_key).

#### 5. Категория: lf_categories

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| name | varchar(80) | Нет | — |
| auction_allowed | boolean | Нет | — |

UNIQUE (name).

PRIMARY KEY (id).

#### 6. Заявка владельца: lf_claims

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| listing_id | bigint | Нет | FK |
| claimant_id | bigint | Нет | FK |
| evidence | text | Нет | — |
| state | varchar(12) | Нет | — |
| created_at | timestamptz | Нет | — |

FOREIGN KEY (claimant_id) REFERENCES lf_users(id).

UNIQUE (id, listing_id).

FOREIGN KEY (listing_id) REFERENCES lf_listings(id).

PRIMARY KEY (id).

#### 7. Жалоба: lf_complaints

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| reporter_id | bigint | Нет | FK |
| listing_id | bigint | Да | FK |
| reported_user_id | bigint | Да | FK |
| reason | text | Нет | — |
| state | varchar(12) | Нет | — |
| moderator_id | bigint | Да | FK |
| resolution | text | Да | — |
| created_at | timestamptz | Нет | — |

FOREIGN KEY (listing_id) REFERENCES lf_listings(id).

FOREIGN KEY (moderator_id) REFERENCES lf_users(id).

PRIMARY KEY (id).

FOREIGN KEY (reported_user_id) REFERENCES lf_users(id).

FOREIGN KEY (reporter_id) REFERENCES lf_users(id).

#### 8. Диалог: lf_conversations

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| claim_id | bigint | Нет | FK |
| created_at | timestamptz | Нет | — |

FOREIGN KEY (claim_id) REFERENCES lf_claims(id).

UNIQUE (claim_id).

PRIMARY KEY (id).

#### 9. Изображение: lf_listing_images

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| listing_id | bigint | Нет | FK |
| object_key | varchar(240) | Нет | — |
| media_type | varchar(20) | Нет | — |
| size_bytes | integer | Нет | — |
| position | smallint | Нет | — |

FOREIGN KEY (listing_id) REFERENCES lf_listings(id) ON DELETE CASCADE.

UNIQUE (listing_id, "position").

UNIQUE (object_key).

PRIMARY KEY (id).

#### 10. Объявление: lf_listings

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| author_id | bigint | Нет | FK |
| category_id | bigint | Нет | FK |
| location_id | bigint | Нет | FK |
| kind | varchar(5) | Нет | — |
| title | varchar(120) | Нет | — |
| description | text | Нет | — |
| event_at | timestamptz | Нет | — |
| event_until | timestamptz | Да | — |
| state | varchar(12) | Нет | — |
| custodian_org_id | bigint | Да | FK |
| official_reported_at | timestamptz | Да | — |
| moderator_id | bigint | Да | FK |
| moderated_at | timestamptz | Да | — |
| created_at | timestamptz | Нет | — |
| search_vector | tsvector | Да | — |

FOREIGN KEY (author_id) REFERENCES lf_users(id).

FOREIGN KEY (category_id) REFERENCES lf_categories(id).

FOREIGN KEY (custodian_org_id) REFERENCES lf_organizations(id).

FOREIGN KEY (location_id) REFERENCES lf_locations(id).

FOREIGN KEY (moderator_id) REFERENCES lf_users(id).

PRIMARY KEY (id).

#### 11. Место: lf_locations

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| city | varchar(80) | Нет | — |
| description | varchar(240) | Нет | — |
| latitude | numeric(9,6) | Да | — |
| longitude | numeric(9,6) | Да | — |
| metro_station | varchar(100) | Да | — |
| organization_id | bigint | Да | FK |

FOREIGN KEY (organization_id) REFERENCES lf_organizations(id).

PRIMARY KEY (id).

#### 12. Сообщение: lf_messages

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| conversation_id | bigint | Нет | FK |
| sender_id | bigint | Нет | FK |
| body | text | Нет | — |
| sent_at | timestamptz | Нет | — |

FOREIGN KEY (conversation_id) REFERENCES lf_conversations(id).

PRIMARY KEY (id).

FOREIGN KEY (sender_id) REFERENCES lf_users(id).

#### 13. Уведомление: lf_notifications

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| user_id | bigint | Нет | FK |
| kind | varchar(40) | Нет | — |
| body | text | Нет | — |
| read_at | timestamptz | Да | — |
| created_at | timestamptz | Нет | — |

PRIMARY KEY (id).

FOREIGN KEY (user_id) REFERENCES lf_users(id).

#### 14. Организация: lf_organizations

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| name | varchar(160) | Нет | — |
| phone | varchar(40) | Нет | — |
| address | text | Да | — |
| instructions | text | Нет | — |
| source_url | text | Нет | — |
| verified_on | date | Нет | — |

UNIQUE (name).

PRIMARY KEY (id).

#### 15. Событие доставки: lf_outbox_events

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| kind | varchar(40) | Нет | — |
| aggregate_id | bigint | Нет | — |
| payload | jsonb | Нет | — |
| attempts | integer | Нет | — |
| next_attempt_at | timestamptz | Нет | — |
| delivered_at | timestamptz | Да | — |
| created_at | timestamptz | Нет | — |

PRIMARY KEY (id).

#### 16. Событие оплаты: lf_payment_events

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| provider_event_id | varchar(100) | Нет | PK |
| order_id | bigint | Нет | FK |
| amount | numeric(12,2) | Нет | — |
| currency | char(3) | Нет | — |
| received_at | timestamptz | Нет | — |

FOREIGN KEY (order_id) REFERENCES lf_payment_orders(id).

PRIMARY KEY (provider_event_id).

#### 17. Заказ оплаты: lf_payment_orders

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| user_id | bigint | Нет | FK |
| tariff_id | bigint | Нет | FK |
| request_key | uuid | Нет | — |
| amount | numeric(12,2) | Нет | — |
| duration_days | integer | Нет | — |
| currency | char(3) | Нет | — |
| state | varchar(12) | Нет | — |
| created_at | timestamptz | Нет | — |
| paid_at | timestamptz | Да | — |

PRIMARY KEY (id).

UNIQUE (request_key).

FOREIGN KEY (tariff_id) REFERENCES lf_tariffs(id).

FOREIGN KEY (user_id) REFERENCES lf_users(id).

#### 18. Контрольный признак: lf_private_attributes

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| listing_id | bigint | Нет | FK |
| name | varchar(80) | Нет | — |
| value | text | Нет | — |

FOREIGN KEY (listing_id) REFERENCES lf_listings(id) ON DELETE CASCADE.

UNIQUE (listing_id, name).

PRIMARY KEY (id).

#### 19. Токен обновления: lf_refresh_tokens

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| user_id | bigint | Нет | FK |
| token_hash | varchar(64) | Нет | — |
| expires_at | timestamptz | Нет | — |
| revoked_at | timestamptz | Да | — |
| created_at | timestamptz | Нет | — |

PRIMARY KEY (id).

UNIQUE (token_hash).

FOREIGN KEY (user_id) REFERENCES lf_users(id).

#### 20. Роль: lf_roles

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| code | varchar(16) | Нет | PK |
| title | varchar(80) | Нет | — |

PRIMARY KEY (code).

#### 21. Период подписки: lf_subscriptions

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| user_id | bigint | Нет | FK |
| payment_order_id | bigint | Нет | FK |
| starts_at | timestamptz | Нет | — |
| ends_at | timestamptz | Нет | — |

FOREIGN KEY (payment_order_id) REFERENCES lf_payment_orders(id).

UNIQUE (payment_order_id).

PRIMARY KEY (id).

FOREIGN KEY (user_id) REFERENCES lf_users(id).

#### 22. Тариф: lf_tariffs

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| title | varchar(80) | Нет | — |
| amount | numeric(12,2) | Нет | — |
| duration_days | integer | Нет | — |
| active | boolean | Нет | — |

PRIMARY KEY (id).

#### 23. Передача: lf_transfers

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| claim_id | bigint | Нет | FK |
| finder_confirmed_at | timestamptz | Да | — |
| owner_confirmed_at | timestamptz | Да | — |
| moderator_id | bigint | Да | FK |
| override_reason | text | Да | — |
| completed_at | timestamptz | Да | — |

FOREIGN KEY (claim_id) REFERENCES lf_claims(id).

UNIQUE (claim_id).

FOREIGN KEY (moderator_id) REFERENCES lf_users(id).

PRIMARY KEY (id).

#### 24. Назначение роли: lf_user_roles

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| user_id | bigint | Нет | PK, FK |
| role_code | varchar(16) | Нет | PK, FK |

PRIMARY KEY (user_id, role_code).

FOREIGN KEY (role_code) REFERENCES lf_roles(code) ON DELETE RESTRICT.

FOREIGN KEY (user_id) REFERENCES lf_users(id) ON DELETE RESTRICT.

#### 25. Пользователь: lf_users

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| email | varchar(254) | Нет | — |
| display_name | varchar(80) | Нет | — |
| password_hash | varchar(100) | Нет | — |
| state | varchar(12) | Нет | — |
| email_confirmed_at | timestamptz | Да | — |
| token_version | integer | Нет | — |
| created_at | timestamptz | Нет | — |

UNIQUE (email).

PRIMARY KEY (id).

#### 26. Одноразовый токен: lf_verification_tokens

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| user_id | bigint | Нет | FK |
| purpose | varchar(16) | Нет | — |
| token_hash | varchar(64) | Нет | — |
| expires_at | timestamptz | Нет | — |
| consumed_at | timestamptz | Да | — |
| created_at | timestamptz | Нет | — |

PRIMARY KEY (id).

UNIQUE (token_hash).

FOREIGN KEY (user_id) REFERENCES lf_users(id).

#### 27. Проверка профиля: lf_verifications

| Поле | Тип PostgreSQL | NULL | Ключ |
| --- | --- | --- | --- |
| id | bigint | Нет | PK |
| user_id | bigint | Нет | FK |
| state | varchar(12) | Нет | — |
| reviewer_id | bigint | Да | FK |
| reason | text | Да | — |
| created_at | timestamptz | Нет | — |
| reviewed_at | timestamptz | Да | — |

PRIMARY KEY (id).

FOREIGN KEY (reviewer_id) REFERENCES lf_users(id).

FOREIGN KEY (user_id) REFERENCES lf_users(id).
