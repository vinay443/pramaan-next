-- Gate the scheduler on onboarding. An application is "onboarded" while active;
-- deboarding sets active = FALSE (the row and its evidence are retained — only
-- new scheduled collection stops).

ALTER TABLE applications ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX ix_applications_active ON applications(active);
