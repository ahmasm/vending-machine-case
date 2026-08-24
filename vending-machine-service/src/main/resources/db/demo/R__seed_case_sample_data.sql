INSERT INTO vending_machine (machine_id, version)
VALUES ('VM-001', 0)
ON CONFLICT (machine_id) DO NOTHING;

INSERT INTO machine_slot (
    machine_id,
    slot_code,
    product_id,
    product_name,
    price_amount,
    price_currency,
    quantity
)
VALUES
    ('VM-001', 'A1', 'WATER', 'Water', 25, 'UNIT', 10),
    ('VM-001', 'A2', 'COKE', 'Coke', 35, 'UNIT', 10),
    ('VM-001', 'A3', 'SODA', 'Soda', 45, 'UNIT', 10),
    ('VM-001', 'A4', 'SNICKERS', 'Snickers', 50, 'UNIT', 10),
    ('VM-001', 'A5', 'CHIPS', 'Chips', 40, 'UNIT', 10),
    ('VM-001', 'B1', 'CANDY_BAR', 'Candy Bar', 30, 'UNIT', 10),
    ('VM-001', 'B2', 'ENERGY_DRINK', 'Energy Drink', 60, 'UNIT', 10),
    ('VM-001', 'B3', 'JUICE_BOX', 'Juice Box', 55, 'UNIT', 10),
    ('VM-001', 'B4', 'PROTEIN_BAR', 'Protein Bar', 45, 'UNIT', 10),
    ('VM-001', 'B5', 'GUM', 'Gum', 20, 'UNIT', 10)
ON CONFLICT (machine_id, slot_code) DO NOTHING;

INSERT INTO cash_inventory (machine_id, denomination, quantity)
VALUES
    ('VM-001', 5, 20),
    ('VM-001', 10, 20),
    ('VM-001', 20, 20),
    ('VM-001', 50, 20)
ON CONFLICT (machine_id, denomination) DO NOTHING;
