-- How payroll treats a working day that has no attendance record and no approved leave.
-- ABSENT (default): the day is loss of pay. PRESENT: the day is paid (only explicit absences reduce pay).
-- Weekly offs, holidays and approved paid leave are never penalised under either value, and days from
-- today onward are never penalised, so a mid-month run does not dock the rest of the month.
INSERT INTO system_settings (key, value, value_type, description) VALUES
('hrm.payroll.unrecorded_day_policy', 'ABSENT', 'STRING',
    'Payroll treatment of working days with no attendance or leave record: ABSENT (loss of pay) or PRESENT (paid)')
ON CONFLICT (key) DO NOTHING;
