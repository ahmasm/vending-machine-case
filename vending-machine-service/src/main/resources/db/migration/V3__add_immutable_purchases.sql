ALTER TABLE purchase_session
    ADD CONSTRAINT uq_purchase_session_id_machine UNIQUE (session_id, machine_id);

CREATE TABLE purchase (
    transaction_id text PRIMARY KEY,
    machine_id text NOT NULL REFERENCES vending_machine (machine_id),
    session_id text NOT NULL UNIQUE,
    slot_code text NOT NULL,
    product_id text NOT NULL,
    product_name text NOT NULL,
    price_amount bigint NOT NULL,
    currency varchar(16) NOT NULL,
    inserted_amount bigint NOT NULL,
    completed_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_purchase_session_machine
        FOREIGN KEY (session_id, machine_id)
        REFERENCES purchase_session (session_id, machine_id),
    CONSTRAINT ck_purchase_transaction_id_not_blank CHECK (btrim(transaction_id) <> ''),
    CONSTRAINT ck_purchase_slot_code_not_blank CHECK (btrim(slot_code) <> ''),
    CONSTRAINT ck_purchase_product_id_not_blank CHECK (btrim(product_id) <> ''),
    CONSTRAINT ck_purchase_product_name_not_blank CHECK (btrim(product_name) <> ''),
    CONSTRAINT ck_purchase_price_positive CHECK (price_amount > 0),
    CONSTRAINT ck_purchase_inserted_covers_price CHECK (inserted_amount >= price_amount),
    CONSTRAINT ck_purchase_currency CHECK (currency = 'UNIT')
);

CREATE INDEX ix_purchase_machine_completed
    ON purchase (machine_id, completed_at);

CREATE TABLE purchase_change (
    transaction_id text NOT NULL REFERENCES purchase (transaction_id) ON DELETE CASCADE,
    denomination smallint NOT NULL,
    quantity integer NOT NULL,
    CONSTRAINT pk_purchase_change PRIMARY KEY (transaction_id, denomination),
    CONSTRAINT ck_purchase_change_denomination CHECK (denomination IN (5, 10, 20, 50)),
    CONSTRAINT ck_purchase_change_quantity_positive CHECK (quantity > 0)
);
