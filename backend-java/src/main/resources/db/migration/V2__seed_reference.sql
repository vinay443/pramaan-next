-- Optional demo applications. Safe to keep: INSERT ... WHERE NOT EXISTS is idempotent.
INSERT INTO applications (id, slug, name, business_unit, criticality, owner, technology, auto_created, created_at, updated_at)
SELECT gen_random_uuid(), 'net-banking', 'Net Banking', 'Retail Banking', 'HIGH', 'app.owner.netbanking', 'Java,Oracle,NGINX', FALSE, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM applications WHERE slug = 'net-banking');

INSERT INTO applications (id, slug, name, business_unit, criticality, owner, technology, auto_created, created_at, updated_at)
SELECT gen_random_uuid(), 'mobile-banking', 'Mobile Banking', 'Retail Banking', 'HIGH', 'app.owner.mobile', 'Kotlin,PostgreSQL,Kubernetes', FALSE, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM applications WHERE slug = 'mobile-banking');

INSERT INTO applications (id, slug, name, business_unit, criticality, owner, technology, auto_created, created_at, updated_at)
SELECT gen_random_uuid(), 'payments', 'Payments', 'Payments', 'CRITICAL', 'app.owner.payments', 'Go,PostgreSQL,Redis', FALSE, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM applications WHERE slug = 'payments');
