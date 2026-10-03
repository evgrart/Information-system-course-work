-- Только отдельная БД для локального PostgreSQL. Не запускать в общей БД studs.
\set ON_ERROR_STOP on
CREATE DATABASE poteryashki_course;
\connect poteryashki_course
CREATE SCHEMA poteryashki;
\set schema poteryashki
\ir create.sql
