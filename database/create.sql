\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
BEGIN;
SET LOCAL search_path TO :"schema", pg_catalog;
\ir 01_tables.sql
\ir 04_handover.sql
\ir 05_search.sql
\ir 02_logic.sql
\ir 03_indexes.sql
COMMIT;
