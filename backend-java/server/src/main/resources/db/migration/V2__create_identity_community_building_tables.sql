-- UrbanSafe Priority Flyway migration V2
-- 身份、小区和楼栋基础表。所有可删除业务对象统一使用 deleted_at 逻辑删除。

CREATE TABLE core.user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(128),
    phone VARCHAR(32),
    email VARCHAR(255),
    organization_name VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    profile JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    remark TEXT
);

CREATE TABLE core.role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    description TEXT,
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE core.user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES core.user_account(id),
    role_id UUID NOT NULL REFERENCES core.role(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE core.community (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_code VARCHAR(64) NOT NULL,
    community_name VARCHAR(255) NOT NULL,
    administrative_region VARCHAR(255),
    address TEXT,
    construction_period VARCHAR(64),
    building_count INTEGER NOT NULL DEFAULT 0 CHECK (building_count >= 0),
    household_count INTEGER NOT NULL DEFAULT 0 CHECK (household_count >= 0),
    resident_count INTEGER NOT NULL DEFAULT 0 CHECK (resident_count >= 0),
    archive_completeness_score NUMERIC(5, 2)
        CHECK (archive_completeness_score BETWEEN 0 AND 100),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    extra_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    remark TEXT
);

CREATE TABLE core.building (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES core.community(id),
    building_code VARCHAR(64) NOT NULL,
    building_name VARCHAR(255),
    address TEXT,
    construction_year SMALLINT
        CHECK (construction_year IS NULL OR construction_year BETWEEN 1800 AND 2200),
    structure_type VARCHAR(64),
    floor_count INTEGER CHECK (floor_count IS NULL OR floor_count > 0),
    building_area NUMERIC(14, 2) CHECK (building_area IS NULL OR building_area >= 0),
    household_count INTEGER NOT NULL DEFAULT 0 CHECK (household_count >= 0),
    resident_count INTEGER NOT NULL DEFAULT 0 CHECK (resident_count >= 0),
    elderly_count INTEGER NOT NULL DEFAULT 0 CHECK (elderly_count >= 0),
    child_count INTEGER NOT NULL DEFAULT 0 CHECK (child_count >= 0),
    has_elevator BOOLEAN NOT NULL DEFAULT FALSE,
    has_illegal_modification BOOLEAN NOT NULL DEFAULT FALSE,
    has_ground_floor_business BOOLEAN NOT NULL DEFAULT FALSE,
    archive_completeness_score NUMERIC(5, 2)
        CHECK (archive_completeness_score BETWEEN 0 AND 100),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    extra_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    remark TEXT
);

COMMENT ON COLUMN core.user_account.deleted_at IS 'MyBatis-Plus 逻辑删除时间，NULL 表示有效';
COMMENT ON COLUMN core.community.deleted_at IS 'MyBatis-Plus 逻辑删除时间，NULL 表示有效';
COMMENT ON COLUMN core.building.deleted_at IS 'MyBatis-Plus 逻辑删除时间，NULL 表示有效';
