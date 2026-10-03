-- UC-01/02: одна незавершённая проверка профиля.
CREATE UNIQUE INDEX lf_verification_pending_uq ON lf_verifications(user_id) WHERE state='pending';
CREATE INDEX lf_roles_reverse_idx ON lf_user_roles(role_code,user_id);
CREATE INDEX lf_refresh_user_idx ON lf_refresh_tokens(user_id,expires_at) WHERE revoked_at IS NULL;
CREATE INDEX lf_verification_token_user_idx ON lf_verification_tokens(user_id,purpose,expires_at) WHERE consumed_at IS NULL;
-- UC-04: проверка доступа и продление оплаченного периода.
CREATE INDEX lf_subscription_access_idx ON lf_subscriptions(user_id,ends_at DESC);
CREATE INDEX lf_payment_user_idx ON lf_payment_orders(user_id,created_at DESC);
CREATE INDEX lf_payment_event_order_idx ON lf_payment_events(order_id);
-- UC-05/06/07: кабинет, очередь модерации, фильтры и полнотекстовый поиск.
CREATE INDEX lf_listing_author_idx ON lf_listings(author_id,id DESC);
CREATE INDEX lf_listing_moderation_idx ON lf_listings(created_at,id) WHERE state='pending';
CREATE INDEX lf_listing_search_idx ON lf_listings(kind,category_id,event_at DESC) WHERE state='published';
CREATE INDEX lf_listing_place_idx ON lf_listings(location_id,event_at DESC) WHERE state='published';
CREATE INDEX lf_listing_feed_idx ON lf_listings(id DESC) WHERE state='published';
CREATE INDEX lf_listing_text_idx ON lf_listings USING gin(search_vector) WHERE state='published';
CREATE INDEX lf_location_organization_idx ON lf_locations(organization_id);
CREATE INDEX lf_listing_category_idx ON lf_listings(category_id);
CREATE INDEX lf_listing_location_idx ON lf_listings(location_id);
CREATE INDEX lf_listing_custodian_idx ON lf_listings(custodian_org_id) WHERE custodian_org_id IS NOT NULL;
-- UC-09/10: один текущий резерв, недублирующиеся открытые заявки.
CREATE UNIQUE INDEX lf_claim_reserve_uq ON lf_claims(listing_id) WHERE state='accepted';
CREATE UNIQUE INDEX lf_claim_open_uq ON lf_claims(listing_id,claimant_id) WHERE state IN ('pending','accepted');
CREATE INDEX lf_claim_claimant_idx ON lf_claims(claimant_id,created_at DESC);
CREATE INDEX lf_claim_listing_idx ON lf_claims(listing_id,state);
CREATE INDEX lf_message_history_idx ON lf_messages(conversation_id,id);
CREATE INDEX lf_message_sender_idx ON lf_messages(sender_id);
-- UC-11/12: очереди служебных решений.
CREATE INDEX lf_complaint_queue_idx ON lf_complaints(created_at,id) WHERE state='pending';
CREATE INDEX lf_complaint_reporter_idx ON lf_complaints(reporter_id);
CREATE INDEX lf_complaint_listing_idx ON lf_complaints(listing_id) WHERE listing_id IS NOT NULL;
CREATE INDEX lf_complaint_user_idx ON lf_complaints(reported_user_id) WHERE reported_user_id IS NOT NULL;
CREATE INDEX lf_permission_listing_idx ON lf_auction_permissions(listing_id);
CREATE INDEX lf_permission_applicant_idx ON lf_auction_permissions(applicant_id);
-- UC-12/13/14: один незавершённый аукцион, победитель и пакет завершения.
CREATE UNIQUE INDEX lf_auction_open_uq ON lf_auctions(listing_id) WHERE state IN ('scheduled','active','suspended');
CREATE INDEX lf_auction_due_idx ON lf_auctions(ends_at,id) WHERE state IN ('scheduled','active');
CREATE INDEX lf_auction_seller_idx ON lf_auctions(seller_id);
CREATE INDEX lf_auction_permission_idx ON lf_auctions(permission_id,listing_id,seller_id);
CREATE INDEX lf_bid_winner_idx ON lf_bids(auction_id,amount DESC,id);
CREATE INDEX lf_bid_user_idx ON lf_bids(bidder_id,placed_at DESC);
-- UC-14/16 и надёжная доставка уведомлений.
CREATE INDEX lf_notification_unread_idx ON lf_notifications(user_id,id DESC) WHERE read_at IS NULL;
CREATE INDEX lf_outbox_pending_idx ON lf_outbox_events(next_attempt_at,id) WHERE delivered_at IS NULL;
CREATE INDEX lf_audit_entity_idx ON lf_audit_entries(entity_table,entity_id,created_at DESC);
CREATE INDEX lf_audit_actor_idx ON lf_audit_entries(actor_id,created_at DESC);
