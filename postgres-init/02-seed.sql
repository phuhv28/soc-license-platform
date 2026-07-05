BEGIN;

-- =========================
-- TENANTS
-- =========================
INSERT INTO tenants (
    tenant_id,
    name,
    status,
    created_at,
    updated_at
)
VALUES
    (
        '11111111-1111-1111-1111-111111111111',
        'Acme Corporation',
        'ACTIVE',
        CURRENT_TIMESTAMP - INTERVAL '90 days',
        CURRENT_TIMESTAMP - INTERVAL '5 days'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'Beta Security',
        'ACTIVE',
        CURRENT_TIMESTAMP - INTERVAL '60 days',
        CURRENT_TIMESTAMP - INTERVAL '2 days'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'Cyber Labs',
        'DISABLED',
        CURRENT_TIMESTAMP - INTERVAL '120 days',
        CURRENT_TIMESTAMP - INTERVAL '30 days'
    ),
    (
        '44444444-4444-4444-4444-444444444444',
        'Delta SOC',
        'ACTIVE',
        CURRENT_TIMESTAMP - INTERVAL '20 days',
        CURRENT_TIMESTAMP
    )
ON CONFLICT DO NOTHING;


-- =========================
-- LICENSES
-- Mỗi tenant chỉ có tối đa 1 ACTIVE license
-- vì có unique partial index:
-- ux_licenses_one_active_per_tenant
-- =========================
INSERT INTO licenses (
    license_id,
    tenant_id,
    eps_quota,
    start_date,
    end_date,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        '11111111-1111-1111-1111-111111111111',
        1000,
        CURRENT_DATE - INTERVAL '30 days',
        CURRENT_DATE + INTERVAL '335 days',
        'ACTIVE',
        CURRENT_TIMESTAMP - INTERVAL '30 days',
        CURRENT_TIMESTAMP
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '11111111-1111-1111-1111-111111111111',
        500,
        CURRENT_DATE - INTERVAL '400 days',
        CURRENT_DATE - INTERVAL '35 days',
        'EXPIRED',
        CURRENT_TIMESTAMP - INTERVAL '400 days',
        CURRENT_TIMESTAMP - INTERVAL '35 days'
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
        '22222222-2222-2222-2222-222222222222',
        2000,
        CURRENT_DATE - INTERVAL '10 days',
        CURRENT_DATE + INTERVAL '20 days',
        'ACTIVE',
        CURRENT_TIMESTAMP - INTERVAL '10 days',
        CURRENT_TIMESTAMP
    ),
    (
        'cccccccc-cccc-cccc-cccc-ccccccccccc1',
        '33333333-3333-3333-3333-333333333333',
        300,
        CURRENT_DATE - INTERVAL '80 days',
        CURRENT_DATE + INTERVAL '280 days',
        'DISABLED',
        CURRENT_TIMESTAMP - INTERVAL '80 days',
        CURRENT_TIMESTAMP - INTERVAL '30 days'
    ),
    (
        'dddddddd-dddd-dddd-dddd-ddddddddddd1',
        '44444444-4444-4444-4444-444444444444',
        750,
        CURRENT_DATE - INTERVAL '15 days',
        CURRENT_DATE + INTERVAL '5 days',
        'ACTIVE',
        CURRENT_TIMESTAMP - INTERVAL '15 days',
        CURRENT_TIMESTAMP
    )
ON CONFLICT DO NOTHING;


-- =========================
-- ALERTS
-- Mỗi tenant + alert_type chỉ có tối đa 1 OPEN alert
-- vì có unique partial index:
-- ux_alerts_one_open_per_tenant_type
-- =========================
INSERT INTO alerts (
    alert_id,
    tenant_id,
    license_id,
    alert_type,
    severity,
    status,
    message,
    threshold_percent,
    current_percent,
    triggered_at,
    resolved_at,
    created_at,
    updated_at
)
VALUES
    (
        '90000000-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'USAGE_70_PERCENT',
        'WARNING',
        'OPEN',
        'Tenant Acme Corporation has reached 75% of EPS quota.',
        70,
        75,
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '2 days',
        CURRENT_TIMESTAMP - INTERVAL '2 days'
    ),
    (
        '90000000-0000-0000-0000-000000000002',
        '22222222-2222-2222-2222-222222222222',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
        'LICENSE_EXPIRING_SOON',
        'WARNING',
        'OPEN',
        'License for Beta Security will expire soon.',
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        CURRENT_TIMESTAMP - INTERVAL '1 day'
    ),
    (
        '90000000-0000-0000-0000-000000000003',
        '44444444-4444-4444-4444-444444444444',
        'dddddddd-dddd-dddd-dddd-ddddddddddd1',
        'LICENSE_EXPIRING_SOON',
        'WARNING',
        'OPEN',
        'License for Delta SOC expires in 5 days.',
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '3 hours',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '3 hours',
        CURRENT_TIMESTAMP - INTERVAL '3 hours'
    ),
    (
        '90000000-0000-0000-0000-000000000004',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'LICENSE_EXPIRED',
        'CRITICAL',
        'RESOLVED',
        'Previous license for Acme Corporation expired and was replaced.',
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '40 days',
        CURRENT_TIMESTAMP - INTERVAL '35 days',
        CURRENT_TIMESTAMP - INTERVAL '40 days',
        CURRENT_TIMESTAMP - INTERVAL '35 days'
    ),
    (
        '90000000-0000-0000-0000-000000000005',
        '33333333-3333-3333-3333-333333333333',
        'cccccccc-cccc-cccc-cccc-ccccccccccc1',
        'USAGE_100_PERCENT',
        'CRITICAL',
        'IGNORED',
        'Cyber Labs reached 100% usage, but tenant is disabled.',
        100,
        100,
        CURRENT_TIMESTAMP - INTERVAL '45 days',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '45 days',
        CURRENT_TIMESTAMP - INTERVAL '30 days'
    )
ON CONFLICT DO NOTHING;


-- =========================
-- AUDIT LOGS
-- =========================
INSERT INTO audit_logs (
    audit_log_id,
    actor,
    action,
    resource_type,
    resource_id,
    before_value,
    after_value,
    created_at
)
VALUES
    (
        '80000000-0000-0000-0000-000000000001',
        'admin',
        'CREATE_TENANT',
        'TENANT',
        '11111111-1111-1111-1111-111111111111',
        NULL,
        '{"name": "Acme Corporation", "status": "ACTIVE"}'::jsonb,
        CURRENT_TIMESTAMP - INTERVAL '90 days'
    ),
    (
        '80000000-0000-0000-0000-000000000002',
        'admin',
        'CREATE_LICENSE',
        'LICENSE',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        NULL,
        '{"tenant": "Acme Corporation", "eps_quota": 1000, "status": "ACTIVE"}'::jsonb,
        CURRENT_TIMESTAMP - INTERVAL '30 days'
    ),
    (
        '80000000-0000-0000-0000-000000000003',
        'system',
        'CREATE_LICENSE',
        'LICENSE',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
        NULL,
        '{"tenant": "Beta Security", "eps_quota": 2000, "status": "ACTIVE"}'::jsonb,
        CURRENT_TIMESTAMP - INTERVAL '10 days'
    ),
    (
        '80000000-0000-0000-0000-000000000004',
        'system',
        'RESOLVE_ALERT',
        'ALERT',
        '90000000-0000-0000-0000-000000000004',
        '{"status": "OPEN"}'::jsonb,
        '{"status": "RESOLVED"}'::jsonb,
        CURRENT_TIMESTAMP - INTERVAL '35 days'
    ),
    (
        '80000000-0000-0000-0000-000000000005',
        'admin',
        'DISABLE_TENANT',
        'TENANT',
        '33333333-3333-3333-3333-333333333333',
        '{"status": "ACTIVE"}'::jsonb,
        '{"status": "DISABLED"}'::jsonb,
        CURRENT_TIMESTAMP - INTERVAL '30 days'
    ),
    (
        '80000000-0000-0000-0000-000000000006',
        'system',
        'IGNORE_ALERT',
        'ALERT',
        '90000000-0000-0000-0000-000000000005',
        '{"status": "OPEN"}'::jsonb,
        '{"status": "IGNORED"}'::jsonb,
        CURRENT_TIMESTAMP - INTERVAL '30 days'
    )
ON CONFLICT DO NOTHING;

COMMIT;


-- =========================
-- VERIFY DATA
-- =========================
SELECT 'tenants' AS table_name, COUNT(*) AS total FROM tenants
UNION ALL
SELECT 'licenses', COUNT(*) FROM licenses
UNION ALL
SELECT 'alerts', COUNT(*) FROM alerts
UNION ALL
SELECT 'audit_logs', COUNT(*) FROM audit_logs;