\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
BEGIN;
SET LOCAL search_path TO :"schema", pg_catalog;
DO $$ BEGIN
 IF obj_description('lf_users'::regclass,'pg_class') IS DISTINCT FROM 'poteryashki.course.v1' THEN
   RAISE EXCEPTION 'Отсутствует маркер курсовой; удаление запрещено';
 END IF;
END $$;
ALTER TABLE lf_auctions DROP CONSTRAINT lf_winner_same_auction;
ALTER TABLE lf_claims DROP CONSTRAINT lf_claim_auction_fk;
DROP VIEW lf_public_listings;
-- Без CASCADE: внешняя зависимость останавливает удаление и откатывает транзакцию.
DROP TABLE lf_messages,lf_conversations,lf_transfers,lf_bids,lf_auctions,
 lf_auction_permissions,lf_complaints,lf_claims,lf_private_attributes,
 lf_listing_images,lf_listings,lf_categories,lf_locations,lf_organizations,
 lf_subscriptions,lf_payment_events,lf_payment_orders,lf_tariffs,
 lf_verifications,lf_verification_tokens,lf_refresh_tokens,lf_user_roles,lf_roles,lf_notifications,
 lf_outbox_events,lf_audit_entries;
-- Функции с типами строк удаляются после удаления зависимых функций ниже.
DROP TABLE lf_users;
DROP FUNCTION lf_search_listings(text,varchar,bigint,bigint,timestamptz,timestamptz,bigint,integer);
DROP FUNCTION lf_search_listings_state(text,varchar,bigint,bigint,timestamptz,timestamptz,bigint,integer,varchar);
DROP PROCEDURE lf_close_due_auctions(integer);
DROP FUNCTION IF EXISTS lf_finalize_auction(bigint),lf_prepare_auction_handover(bigint),lf_place_bid(bigint,bigint,numeric,uuid),
 lf_confirm_transfer(bigint,bigint),lf_resolve_return(bigint,bigint,text),lf_reserve_found(bigint,bigint),
 lf_activate_subscription(bigint,varchar,numeric,char),lf_bid_guard(),lf_auction_guard(),
 lf_permission_guard(),lf_message_guard(),lf_transfer_guard(),lf_reserved_integrity(),lf_claim_guard(),
 lf_listing_guard(),lf_subscription_guard(),lf_payment_guard(),lf_verification_guard(),lf_consume_token(varchar,varchar),
 lf_user_guard(),lf_blocked_auctions(),lf_audit_change(),lf_immutable(),lf_assert_moderator(bigint),lf_assert_user(bigint,boolean);
COMMIT;
