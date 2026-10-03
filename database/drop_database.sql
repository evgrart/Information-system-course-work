-- Удаляет только отдельную локальную БД. На helios используется drop.sql.
\set ON_ERROR_STOP on
\connect postgres
DROP DATABASE poteryashki_course;
