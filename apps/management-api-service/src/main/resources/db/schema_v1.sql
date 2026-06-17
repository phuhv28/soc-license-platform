CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TABLE tenants (
    tenant_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tenants_status
        CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_tenants_status
    ON tenants (status);

CREATE INDEX idx_tenants_name
    ON tenants (name);

CREATE TRIGGER trg_tenants_updated_at
BEFORE UPDATE ON tenants
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TABLE licenses (
    license_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL,
    eps_quota  INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date   DATE NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_licenses_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (tenant_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_licenses_eps_quota
        CHECK (eps_quota > 0),

    CONSTRAINT chk_licenses_date_range
        CHECK (start_date <= end_date),

    CONSTRAINT chk_licenses_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'DISABLED'))
);

CREATE UNIQUE INDEX ux_licenses_one_active_per_tenant
    ON licenses (tenant_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_licenses_tenant_id
    ON licenses (tenant_id);

CREATE INDEX idx_licenses_status
    ON licenses (status);

CREATE INDEX idx_licenses_end_date
    ON licenses (end_date);

CREATE INDEX idx_licenses_start_end_date
    ON licenses (start_date, end_date);

CREATE TRIGGER trg_licenses_updated_at
BEFORE UPDATE ON licenses
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


CREATE TABLE audit_logs (
    audit_log_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor         VARCHAR(100) NOT NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id   UUID,
    before_value  JSONB,
    after_value   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_audit_logs_action
        CHECK (
            action IN (
                'CREATE_TENANT',
                'UPDATE_TENANT',
                'DISABLE_TENANT',
                'CREATE_LICENSE',
                'UPDATE_LICENSE',
                'DISABLE_LICENSE',
                'RESOLVE_ALERT',
                'IGNORE_ALERT'
            )
        ),

    CONSTRAINT chk_audit_logs_resource_type
        CHECK (
            resource_type IN (
                'TENANT',
                'LICENSE',
                'ALERT'
            )
        )
);

CREATE INDEX idx_audit_logs_actor
    ON audit_logs (actor);

CREATE INDEX idx_audit_logs_action
    ON audit_logs (action);

CREATE INDEX idx_audit_logs_resource
    ON audit_logs (resource_type, resource_id);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs (created_at);


CREATE TABLE alerts (
    alert_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    license_id        UUID,
    alert_type        VARCHAR(100) NOT NULL,
    severity          VARCHAR(50) NOT NULL,
    status            VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    message           TEXT NOT NULL,
    threshold_percent INTEGER,
    current_percent   INTEGER,
    triggered_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alerts_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants (tenant_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_alerts_license
        FOREIGN KEY (license_id)
        REFERENCES licenses (license_id)
        ON DELETE SET NULL,

    CONSTRAINT chk_alerts_type
        CHECK (
            alert_type IN (
                'LICENSE_EXPIRING_SOON',
                'LICENSE_EXPIRED',
                'USAGE_70_PERCENT',
                'USAGE_100_PERCENT'
            )
        ),

    CONSTRAINT chk_alerts_severity
        CHECK (
            severity IN (
                'INFO',
                'WARNING',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_alerts_status
        CHECK (
            status IN (
                'OPEN',
                'RESOLVED',
                'IGNORED'
            )
        ),

    CONSTRAINT chk_alerts_threshold_percent
        CHECK (
            threshold_percent IS NULL
            OR threshold_percent >= 0
        ),

    CONSTRAINT chk_alerts_current_percent
        CHECK (
            current_percent IS NULL
            OR current_percent >= 0
        )
);

CREATE INDEX idx_alerts_tenant_id
    ON alerts (tenant_id);

CREATE INDEX idx_alerts_license_id
    ON alerts (license_id);

CREATE INDEX idx_alerts_type
    ON alerts (alert_type);

CREATE INDEX idx_alerts_status
    ON alerts (status);

CREATE INDEX idx_alerts_triggered_at
    ON alerts (triggered_at);

CREATE UNIQUE INDEX ux_alerts_one_open_per_tenant_type
    ON alerts (tenant_id, alert_type)
    WHERE status = 'OPEN';

CREATE TRIGGER trg_alerts_updated_at
BEFORE UPDATE ON alerts
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
