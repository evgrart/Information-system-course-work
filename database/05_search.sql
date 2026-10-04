CREATE OR REPLACE FUNCTION lf_search_listings_state(p_text text,p_kind varchar,p_category bigint,
 p_location bigint,p_from timestamptz,p_to timestamptz,p_before bigint,p_limit integer,p_state varchar)
RETURNS TABLE(id bigint,title varchar,kind varchar,event_at timestamptz,location_id bigint)
LANGUAGE plpgsql STABLE SET search_path FROM CURRENT AS $$
BEGIN
 IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 100 OR (p_from IS NOT NULL AND p_to IS NOT NULL AND p_from>p_to)
   OR (p_state IS NOT NULL AND p_state NOT IN ('published','reserved','returned','auctioned')) THEN
   RAISE EXCEPTION 'Некорректные параметры поиска';
 END IF;
 RETURN QUERY SELECT l.id,l.title,l.kind,l.event_at,l.location_id FROM lf_listings l
 WHERE l.state IN ('published','reserved','returned','auctioned') AND (p_state IS NULL OR l.state=p_state)
   AND (p_text IS NULL OR l.search_vector @@ plainto_tsquery('russian',p_text))
   AND (p_kind IS NULL OR l.kind=p_kind) AND (p_category IS NULL OR l.category_id=p_category)
   AND (p_location IS NULL OR l.location_id=p_location) AND (p_from IS NULL OR l.event_at>=p_from)
   AND (p_to IS NULL OR l.event_at<=p_to) AND (p_before IS NULL OR l.id<p_before)
 ORDER BY l.id DESC LIMIT p_limit;
END $$;
-- Старый контракт сохраняет поиск только активных объявлений.
CREATE OR REPLACE FUNCTION lf_search_listings(p_text text DEFAULT NULL,p_kind varchar DEFAULT NULL,p_category bigint DEFAULT NULL,
 p_location bigint DEFAULT NULL,p_from timestamptz DEFAULT NULL,p_to timestamptz DEFAULT NULL,
 p_before bigint DEFAULT NULL,p_limit integer DEFAULT 20)
RETURNS TABLE(id bigint,title varchar,kind varchar,event_at timestamptz,location_id bigint)
LANGUAGE plpgsql STABLE SET search_path FROM CURRENT AS $$
BEGIN
 RETURN QUERY SELECT * FROM lf_search_listings_state(p_text,p_kind,p_category,p_location,p_from,p_to,p_before,p_limit,'published');
END $$;
