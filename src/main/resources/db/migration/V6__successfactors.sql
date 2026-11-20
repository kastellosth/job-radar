-- SAP SuccessFactors career sites (EY, Vodafone, Kotsovolos...). The slug of such a company is its search,
-- e.g. 'careers.ey.com/ey?locationsearch=Greece'.
ALTER TABLE tracked_company DROP CONSTRAINT tracked_company_provider_check;
ALTER TABLE tracked_company ADD CONSTRAINT tracked_company_provider_check
    CHECK (provider IN ('WORKABLE', 'SMARTRECRUITERS', 'GREENHOUSE', 'LEVER', 'WORKDAY', 'SUCCESSFACTORS'));
