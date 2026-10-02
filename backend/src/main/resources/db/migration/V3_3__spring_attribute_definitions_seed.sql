-- Phase 1 / task 1.9 - the attribute-definition catalogue per spring type (DESIGN.md sections 2.3, 6.3, 14 item 11).
--
-- A definition describes one attribute of a spring type: label, data type, unit, whether it is required for an
-- ACTIVE product, numeric limits and permitted values. The product form and the validator are generated from
-- these rows, so adding a row (SQL or a later migration) changes both without code.
--
-- Where an attribute_code equals a column of "products" (wireDiameter, outerDiameter, innerDiameter, freeLength,
-- numberOfCoils, activeCoils, springRate, maxLoad, minLoad, workingLength, solidHeight, endType) the value is
-- stored in that column; every other code is stored in spring_specifications.attributes (JSONB). A definition may
-- relabel a column for its type (e.g. outerDiameter is "Body outer diameter" for extension springs).
-- CUSTOM springs have no fixed attributes: they accept free-form key/value specifications.

INSERT INTO spring_attribute_definitions
  (spring_type, attribute_code, label, data_type, unit, required, min_value, max_value, enum_values, display_order) VALUES
-- COMPRESSION: wire diameter, outer diameter, free length, coil count, spring rate, load, solid height, end type
  ('COMPRESSION', 'wireDiameter',   'Wire diameter',   'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 10),
  ('COMPRESSION', 'outerDiameter',  'Outer diameter',  'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 20),
  ('COMPRESSION', 'freeLength',     'Free length',     'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 30),
  ('COMPRESSION', 'numberOfCoils',  'Number of coils', 'NUMBER', NULL,   TRUE,  0.25,  NULL, NULL, 40),
  ('COMPRESSION', 'activeCoils',    'Active coils',    'NUMBER', NULL,   FALSE, 0.25,  NULL, NULL, 50),
  ('COMPRESSION', 'springRate',     'Spring rate',     'NUMBER', 'N/mm', FALSE, 0,     NULL, NULL, 60),
  ('COMPRESSION', 'maxLoad',        'Max load',        'NUMBER', 'N',    FALSE, 0,     NULL, NULL, 70),
  ('COMPRESSION', 'solidHeight',    'Solid height',    'NUMBER', 'mm',   FALSE, 0,     NULL, NULL, 80),
  ('COMPRESSION', 'endType',        'End type',        'ENUM',   NULL,   FALSE, NULL,  NULL, '["PLAIN","PLAIN_GROUND","CLOSED","CLOSED_GROUND"]', 90),
  ('COMPRESSION', 'coilDirection',  'Coil direction',  'ENUM',   NULL,   FALSE, NULL,  NULL, '["RIGHT","LEFT"]', 100),
-- EXTENSION: wire diameter, body diameter, free length, coil count, hook type, hook length, spring rate
  ('EXTENSION', 'wireDiameter',     'Wire diameter',        'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 10),
  ('EXTENSION', 'outerDiameter',    'Body outer diameter',  'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 20),
  ('EXTENSION', 'freeLength',       'Free length (inside hooks)', 'NUMBER', 'mm', TRUE, 0.001, NULL, NULL, 30),
  ('EXTENSION', 'numberOfCoils',    'Number of coils',      'NUMBER', NULL,   TRUE,  0.25,  NULL, NULL, 40),
  ('EXTENSION', 'springRate',       'Spring rate',          'NUMBER', 'N/mm', FALSE, 0,     NULL, NULL, 50),
  ('EXTENSION', 'hookType',         'Hook type',            'ENUM',   NULL,   TRUE,  NULL,  NULL, '["MACHINE_HOOK","CROSSOVER","SIDE_HOOK","DOUBLE_LOOP","EXTENDED_LOOP"]', 60),
  ('EXTENSION', 'hookLength',       'Hook length',          'NUMBER', 'mm',   FALSE, 0,     NULL, NULL, 70),
  ('EXTENSION', 'initialTension',   'Initial tension',      'NUMBER', 'N',    FALSE, 0,     NULL, NULL, 80),
-- TORSION: wire diameter, coil diameter, number of coils, leg length, leg angle, torque
  ('TORSION', 'wireDiameter',       'Wire diameter',        'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 10),
  ('TORSION', 'outerDiameter',      'Coil outer diameter',  'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 20),
  ('TORSION', 'numberOfCoils',      'Number of coils',      'NUMBER', NULL,   TRUE,  0.25,  NULL, NULL, 30),
  ('TORSION', 'legLength',          'Leg length',           'NUMBER', 'mm',   TRUE,  0,     NULL, NULL, 40),
  ('TORSION', 'legAngle',           'Leg angle',            'NUMBER', 'deg',  TRUE,  0,     360,  NULL, 50),
  ('TORSION', 'torque',             'Torque',               'NUMBER', 'N.mm', FALSE, 0,     NULL, NULL, 60),
  ('TORSION', 'coilDirection',      'Coil direction',       'ENUM',   NULL,   FALSE, NULL,  NULL, '["RIGHT","LEFT"]', 70),
-- CONICAL (attribute set assumed; confirm with Engineering)
  ('CONICAL', 'wireDiameter',       'Wire diameter',            'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 10),
  ('CONICAL', 'outerDiameter',      'Large end outer diameter', 'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 20),
  ('CONICAL', 'smallOuterDiameter', 'Small end outer diameter', 'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 30),
  ('CONICAL', 'freeLength',         'Free length',              'NUMBER', 'mm',   TRUE,  0.001, NULL, NULL, 40),
  ('CONICAL', 'numberOfCoils',      'Number of coils',          'NUMBER', NULL,   TRUE,  0.25,  NULL, NULL, 50),
  ('CONICAL', 'springRate',         'Spring rate',              'NUMBER', 'N/mm', FALSE, 0,     NULL, NULL, 60),
  ('CONICAL', 'maxLoad',            'Max load',                 'NUMBER', 'N',    FALSE, 0,     NULL, NULL, 70),
-- DISC_BELLEVILLE: outer/inner diameter, thickness, free height, load, stack (decision 11 default)
  ('DISC_BELLEVILLE', 'outerDiameter',    'Outer diameter',  'NUMBER', 'mm', TRUE,  0.001, NULL, NULL, 10),
  ('DISC_BELLEVILLE', 'innerDiameter',    'Inner diameter',  'NUMBER', 'mm', TRUE,  0.001, NULL, NULL, 20),
  ('DISC_BELLEVILLE', 'thickness',        'Thickness',       'NUMBER', 'mm', TRUE,  0.001, NULL, NULL, 30),
  ('DISC_BELLEVILLE', 'freeLength',       'Free height',     'NUMBER', 'mm', TRUE,  0.001, NULL, NULL, 40),
  ('DISC_BELLEVILLE', 'maxLoad',          'Load at flat',    'NUMBER', 'N',  FALSE, 0,     NULL, NULL, 50),
  ('DISC_BELLEVILLE', 'stackCount',       'Discs in stack',  'NUMBER', NULL, FALSE, 1,     NULL, NULL, 60),
  ('DISC_BELLEVILLE', 'stackArrangement', 'Stack arrangement', 'ENUM', NULL, FALSE, NULL,  NULL, '["SERIES","PARALLEL","MIXED"]', 70),
-- WIRE_FORM (attribute set assumed; confirm with Engineering)
  ('WIRE_FORM', 'wireDiameter',     'Wire diameter',   'NUMBER', 'mm', TRUE,  0.001, NULL, NULL, 10),
  ('WIRE_FORM', 'developedLength',  'Developed length', 'NUMBER', 'mm', TRUE, 0.001, NULL, NULL, 20),
  ('WIRE_FORM', 'numberOfBends',    'Number of bends', 'NUMBER', NULL, FALSE, 0,     NULL, NULL, 30),
  ('WIRE_FORM', 'formDescription',  'Form description', 'TEXT',  NULL, FALSE, NULL,  NULL, NULL, 40);
