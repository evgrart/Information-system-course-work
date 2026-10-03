-- Экспорт словаря: psql -X -w -qAt -v schema=s465826 -f database/catalog.sql
\set ON_ERROR_STOP on
\if :{?schema}
\else
  \set schema s465826
\endif
SELECT jsonb_agg(jsonb_build_object(
 'name',c.relname,
 'columns',(SELECT jsonb_agg(jsonb_build_object(
   'name',a.attname,'type',format_type(a.atttypid,a.atttypmod),
   'nullable',NOT a.attnotnull,'default',pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum)
   FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
   WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped),
 'constraints',(SELECT jsonb_agg(jsonb_build_object(
   'name',conname,'type',contype,'definition',pg_get_constraintdef(oid)) ORDER BY conname)
   FROM pg_constraint WHERE conrelid=c.oid)) ORDER BY c.relname)
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname=:'schema' AND c.relkind='r' AND c.relname LIKE 'lf\_%' ESCAPE '\';
