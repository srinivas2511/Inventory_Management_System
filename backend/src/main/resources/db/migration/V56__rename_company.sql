-- Rename the seeded company: remove "Spring" from display and legal name
UPDATE companies
SET name       = 'Manufacturing Co.',
    legal_name = 'Manufacturing Company Pvt Ltd'
WHERE company_code = 'SMC001';
