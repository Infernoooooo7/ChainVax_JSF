-- Admin console: ADMIN role. Admins manage accounts and master data through a separate login
-- (/admin); they cannot perform operational actions (transfers, stock issue, releases).
alter table app_user drop constraint app_user_role_check;
alter table app_user add constraint app_user_role_check
    check (role in ('STOREKEEPER', 'TRANSPORTER', 'DISTRICT_OFFICER', 'AUDITOR', 'ADMIN'));
