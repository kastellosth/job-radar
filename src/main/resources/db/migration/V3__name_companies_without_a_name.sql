-- Lever's API has no company name, so companies added from a Lever link were saved under their slug
-- ("kaizen-gaming"). Give them the same readable name a new one now gets ("Kaizen Gaming").
UPDATE tracked_company
SET name = initcap(regexp_replace(slug, '[-_.]+', ' ', 'g'))
WHERE provider IN ('LEVER', 'GREENHOUSE')
  AND name = slug;
