-- Workday career sites (Accenture, PwC...). The slug of a Workday company is its filtered search, e.g.
-- 'accenture.wd103/AccentureCareers?locationCountry=566388c1eb974c42bd9e3da4c2f57d60'.
ALTER TABLE tracked_company DROP CONSTRAINT tracked_company_provider_check;
ALTER TABLE tracked_company ADD CONSTRAINT tracked_company_provider_check
    CHECK (provider IN ('WORKABLE', 'SMARTRECRUITERS', 'GREENHOUSE', 'LEVER', 'WORKDAY'));
