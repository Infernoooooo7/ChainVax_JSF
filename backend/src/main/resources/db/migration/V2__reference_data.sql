-- Reference data for the demo district. Site names are illustrative demo data.

insert into site (code, name, type, district) values
    ('DVS-UDP', 'District Vaccine Store, Udupi (demo)', 'DISTRICT_STORE', 'Udupi'),
    ('VAN-01',  'Cold-chain van KA-20-CV-0101 (demo)',  'TRANSPORT_VAN',  'Udupi'),
    ('PHC-NTE', 'Primary Health Centre, Nitte (demo)',  'HEALTH_CENTRE',  'Udupi');

-- Two excursion policies.
-- DEMO-FAST is for the live presentation only: durations are compressed to seconds.
-- PQS-E006-T1 mirrors the factory alarm settings of WHO PQS E006/TR06.4 Type 1 (+2 to +8 C)
-- 30-day loggers. Treating an alarm as an automatic CONDEMNED decision is a ChainVax design
-- choice, not WHO discard guidance.
insert into excursion_policy (code, name, basis, demo_only, heat_alarm_c, cold_alarm_c,
                              at_risk_after_seconds, heat_condemn_after_seconds, cold_condemn_after_seconds,
                              version, updated_by, updated_at)
values ('DEMO-FAST', 'Demo policy (compressed time)',
        'DEMO ONLY - NOT MEDICAL GUIDANCE. Same alarm thresholds as WHO PQS E006/TR06.4 Type 1 (+8 C or above, -0.5 C or below) but durations compressed to seconds so the VIABLE -> AT_RISK -> CONDEMNED lifecycle can be shown live.',
        true, 8.00, -0.50, 0, 90, 60, 0, 'flyway', now()),
       ('PQS-E006-T1', 'Reference policy (PQS E006 Type 1 alarm durations)',
        'Thresholds and durations mirror the factory alarm settings in WHO PQS E006/TR06.4 (revised 10 Jan 2022) for Type 1 30-day refrigerator loggers: high alarm +8 C or above for 10 hours, low alarm -0.5 C or below for 60 minutes. Using an alarm duration as an automatic CONDEMNED decision is a ChainVax design choice; real programmes assess VVM status / shake test before discarding.',
        false, 8.00, -0.50, 0, 36000, 3600, 0, 'flyway', now());
