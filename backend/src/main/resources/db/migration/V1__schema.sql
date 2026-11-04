-- ChainVax schema (PostgreSQL). Flyway owns the schema; Hibernate runs with ddl-auto=validate.

create table site (
    id       bigserial primary key,
    code     varchar(40)  not null unique,
    name     varchar(120) not null,
    type     varchar(30)  not null check (type in ('DISTRICT_STORE', 'TRANSPORT_VAN', 'HEALTH_CENTRE')),
    district varchar(80)  not null
);

create table app_user (
    id            bigserial primary key,
    username      varchar(60)  not null unique,
    password_hash varchar(100) not null,
    full_name     varchar(120) not null,
    role          varchar(30)  not null check (role in ('STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER', 'AUDITOR')),
    site_id       bigint references site (id),
    enabled       boolean      not null default true
);

-- Configurable excursion policy. Readings >= heat_alarm_c are HEAT, <= cold_alarm_c are COLD.
create table excursion_policy (
    id                         bigserial primary key,
    code                       varchar(40)   not null unique,
    name                       varchar(120)  not null,
    basis                      varchar(1000) not null,
    demo_only                  boolean       not null,
    heat_alarm_c               numeric(5, 2) not null,
    cold_alarm_c               numeric(5, 2) not null,
    at_risk_after_seconds      integer       not null check (at_risk_after_seconds >= 0),
    heat_condemn_after_seconds integer       not null check (heat_condemn_after_seconds > 0),
    cold_condemn_after_seconds integer       not null check (cold_condemn_after_seconds > 0),
    version                    bigint        not null default 0,
    updated_by                 varchar(60),
    updated_at                 timestamptz,
    check (cold_alarm_c < heat_alarm_c)
);

-- A vaccine batch (one lot of cartons) that moves through custody as a unit.
create table vaccine_batch (
    id                bigserial primary key,
    batch_code        varchar(40) not null unique,
    product           varchar(80) not null,
    lot_number        varchar(40) not null,
    manufacturer      varchar(80),
    expiry_date       date        not null,
    doses_per_vial    integer     not null check (doses_per_vial > 0),
    initial_vials     integer     not null check (initial_vials >= 0),
    vials_on_hand     integer     not null check (vials_on_hand >= 0),
    status            varchar(20) not null check (status in ('VIABLE', 'AT_RISK', 'CONDEMNED')),
    status_reason     varchar(500),
    status_changed_at timestamptz not null,
    current_site_id   bigint      not null references site (id),
    current_custodian varchar(60),
    policy_id         bigint      not null references excursion_policy (id),
    version           bigint      not null default 0   -- JPA @Version (optimistic locking)
);
create index ix_batch_site on vaccine_batch (current_site_id);

create table batch_status_event (
    id          bigserial primary key,
    batch_id    bigint       not null references vaccine_batch (id),
    from_status varchar(20),
    to_status   varchar(20)  not null,
    reason      varchar(500) not null,
    actor       varchar(80)  not null,
    site_code   varchar(40),
    custodian   varchar(60),
    occurred_at timestamptz  not null
);
create index ix_status_event_batch on batch_status_event (batch_id);

create table logger_device (
    id                 bigserial primary key,
    device_code        varchar(40)  not null unique,
    site_id            bigint       not null references site (id),
    secret_hash        varchar(100) not null,
    description        varchar(160),
    last_reading_at    timestamptz,
    last_temperature_c numeric(5, 2)
);

create table temperature_reading (
    id            bigserial primary key,
    device_id     bigint        not null references logger_device (id),
    site_id       bigint        not null references site (id),
    recorded_at   timestamptz   not null,
    received_at   timestamptz   not null,
    temperature_c numeric(5, 2) not null,
    source        varchar(20)   not null check (source in ('LOGGER', 'SIMULATOR')),
    late          boolean       not null default false
);
create index ix_reading_device_time on temperature_reading (device_id, recorded_at);

-- One row per continuous out-of-range period experienced by a batch.
create table excursion_window (
    id                 bigserial primary key,
    batch_id           bigint        not null references vaccine_batch (id),
    site_id            bigint        not null references site (id),
    device_id          bigint        not null references logger_device (id),
    custodian          varchar(60),
    type               varchar(10)   not null check (type in ('HEAT', 'COLD')),
    started_at         timestamptz   not null,
    last_reading_at    timestamptz   not null,
    ended_at           timestamptz,
    peak_temperature_c numeric(5, 2) not null,
    is_open            boolean       not null,
    outcome            varchar(20)
);
create unique index ux_one_open_window_per_batch on excursion_window (batch_id) where is_open;

-- Tamper-evident custody ledger: hash = SHA-256(previous_hash + payload).
create table custody_ledger (
    seq            bigint primary key,
    batch_id       bigint        not null references vaccine_batch (id),
    batch_code     varchar(40)   not null,
    event_type     varchar(30)   not null check (event_type in ('REGISTERED', 'TRANSFER')),
    from_site_code varchar(40),
    to_site_code   varchar(40)   not null,
    from_custodian varchar(60),
    to_custodian   varchar(60)   not null,
    vials          integer       not null,
    batch_status   varchar(20)   not null,
    occurred_at    timestamptz   not null,
    recorded_by    varchar(60)   not null,
    note           varchar(240),
    payload        varchar(4000) not null,
    previous_hash  varchar(64)   not null,
    hash           varchar(64)   not null unique
);
create index ix_ledger_batch on custody_ledger (batch_id);

-- Single-row head pointer; locked FOR UPDATE to serialise appends.
create table ledger_head (
    id        integer primary key check (id = 1),
    last_seq  bigint      not null,
    last_hash varchar(64) not null
);
insert into ledger_head (id, last_seq, last_hash)
values (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');

-- Officer-attributed overrides (FEFO bypass, AT_RISK release, policy change).
create table override_audit (
    id               bigserial primary key,
    type             varchar(40)   not null,
    batch_id         bigint references vaccine_batch (id),
    batch_code       varchar(40),
    officer_username varchar(60)   not null,
    officer_name     varchar(120)  not null,
    reason           varchar(1000) not null,
    details          varchar(2000) not null,
    created_at       timestamptz   not null
);

create table stock_issue (
    id                bigserial primary key,
    batch_id          bigint      not null references vaccine_batch (id),
    site_code         varchar(40) not null,
    vials             integer     not null check (vials > 0),
    issued_by         varchar(60) not null,
    issued_at         timestamptz not null,
    fefo_order        boolean     not null,
    override_audit_id bigint references override_audit (id),
    purpose           varchar(160)
);

create table alert_digest (
    id                bigserial primary key,
    generated_at      timestamptz   not null,
    trigger_source    varchar(20)   not null,
    headline          varchar(200)  not null,
    body              varchar(8000) not null,
    ledger_head_hash  varchar(64),
    ledger_entries    bigint        not null,
    open_excursions   integer       not null,
    at_risk_batches   integer       not null,
    condemned_batches integer       not null,
    expiring_soon     integer       not null
);

-- Hibernate Envers revision history for batches and policies (who changed what, when).
create table audit_revision (
    id                 bigserial primary key,
    revision_timestamp bigint      not null,
    username           varchar(80) not null
);

create table vaccine_batch_aud (
    id                bigint not null,
    rev               bigint not null references audit_revision (id),
    revtype           smallint,
    batch_code        varchar(40),
    product           varchar(80),
    lot_number        varchar(40),
    manufacturer      varchar(80),
    expiry_date       date,
    doses_per_vial    integer,
    initial_vials     integer,
    vials_on_hand     integer,
    status            varchar(20),
    status_reason     varchar(500),
    status_changed_at timestamptz,
    current_site_id   bigint,
    current_custodian varchar(60),
    policy_id         bigint,
    primary key (id, rev)
);

create table excursion_policy_aud (
    id                         bigint not null,
    rev                        bigint not null references audit_revision (id),
    revtype                    smallint,
    code                       varchar(40),
    name                       varchar(120),
    basis                      varchar(1000),
    demo_only                  boolean,
    heat_alarm_c               numeric(5, 2),
    cold_alarm_c               numeric(5, 2),
    at_risk_after_seconds      integer,
    heat_condemn_after_seconds integer,
    cold_condemn_after_seconds integer,
    updated_by                 varchar(60),
    updated_at                 timestamptz,
    primary key (id, rev)
);
