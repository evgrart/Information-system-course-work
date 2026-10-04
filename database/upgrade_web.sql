\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
BEGIN;
SET LOCAL search_path TO :"schema", pg_catalog;
DO $$ BEGIN
 IF obj_description('lf_users'::regclass,'pg_class') IS DISTINCT FROM 'poteryashki.course.v1' THEN
   RAISE EXCEPTION 'Отсутствует маркер курсовой; обновление запрещено';
 END IF;
END $$;
\ir 04_handover.sql
\ir 05_search.sql
-- Завершённые ранее торги также получают дело передачи; повторный вызов безопасен.
SELECT lf_prepare_auction_handover(id) FROM lf_auctions WHERE state='finished' AND winner_bid_id IS NOT NULL;
-- Старый демонстрационный набор создавал диалог только для первой заявки.
INSERT INTO lf_conversations(claim_id)
SELECT c.id FROM lf_claims c
WHERE NOT EXISTS (SELECT 1 FROM lf_conversations d WHERE d.claim_id=c.id)
ON CONFLICT (claim_id) DO NOTHING;
COMMIT;
