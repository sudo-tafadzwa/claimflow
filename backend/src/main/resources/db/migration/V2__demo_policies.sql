-- Fictional policies for the demo and tests. No real people or insurers.
INSERT INTO policy (policy_number, holder_name, policy_type, cover_limit, deductible, start_date, end_date) VALUES
    ('TRV-100234', 'Rudo Moyo',        'TRAVEL', 5000.00,  100.00, DATE '2026-01-01', DATE '2026-12-31'),
    ('TRV-100871', 'Daniel Okafor',    'TRAVEL', 3000.00,   50.00, DATE '2026-09-01', DATE '2027-08-31'),
    ('MTR-220415', 'Amelia Novak',     'MOTOR', 25000.00,  250.00, DATE '2025-11-15', DATE '2026-11-14'),
    ('MTR-220988', 'Kofi Mensah',      'MOTOR', 15000.00,  500.00, DATE '2026-03-01', DATE '2027-02-28'),
    ('HOM-310062', 'Sarah Lindqvist',  'HOME',  80000.00, 1000.00, DATE '2025-06-01', DATE '2026-05-31'),
    ('HOM-310547', 'Tendai Chikore',   'HOME', 120000.00,  750.00, DATE '2026-02-01', DATE '2027-01-31');
