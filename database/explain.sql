\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
BEGIN;
SET LOCAL search_path TO :"schema", pg_catalog;
-- Нагрузочный набор не сохраняется. Основные данные не изменяются.
INSERT INTO lf_listings(author_id,category_id,location_id,kind,title,description,event_at,state,moderator_id,moderated_at)
 SELECT 3,1+(i%2),1+(i%2),'found',CASE WHEN i%1000=0 THEN 'Термос' ELSE 'Предмет '||i END,
 'Синтетическое объявление для анализа индексов.',clock_timestamp()-i*interval '1 minute',
 CASE WHEN i%5=0 THEN 'published' ELSE 'archived' END,1,clock_timestamp() FROM generate_series(1,10000) AS s(i);
ANALYZE lf_listings;
-- В рабочей БД это также выполняется обслуживанием GIN при autovacuum.
SELECT gin_clean_pending_list('lf_listing_text_idx'::regclass);
\echo Полнотекстовый поиск: индекс GIN
EXPLAIN (ANALYZE,BUFFERS) SELECT id,title FROM lf_listings
 WHERE state='published' AND search_vector @@ plainto_tsquery('russian','термос');
\echo Поиск по месту и интервалу: составной частичный индекс
EXPLAIN (ANALYZE,BUFFERS) SELECT id,title FROM lf_listings WHERE state='published'
 AND location_id=1 AND event_at>=statement_timestamp()-interval '2 hours' ORDER BY event_at DESC;
\echo Лента с курсором: частичный B-tree
SELECT max(id)-1000 AS before_id FROM lf_listings \gset
EXPLAIN (ANALYZE,BUFFERS) SELECT id,title FROM lf_listings WHERE state='published'
 AND id<:before_id ORDER BY id DESC LIMIT 20;
\echo Определение победителя: составной B-tree (малый учебный набор)
EXPLAIN (ANALYZE,BUFFERS) SELECT id FROM lf_bids WHERE auction_id=1 ORDER BY amount DESC,id LIMIT 1;
DROP INDEX lf_listing_text_idx;
\echo Полнотекстовый поиск без GIN на тех же данных
EXPLAIN (ANALYZE,BUFFERS) SELECT id,title FROM lf_listings
 WHERE state='published' AND search_vector @@ plainto_tsquery('russian','термос');
ROLLBACK;
SET search_path TO :"schema", pg_catalog;
ANALYZE lf_listings;
