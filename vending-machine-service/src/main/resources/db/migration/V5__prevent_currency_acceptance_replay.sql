CREATE TABLE currency_acceptance (
    machine_id text NOT NULL,
    validator_reference_hash varchar(64) NOT NULL,
    consumed_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_currency_acceptance
        PRIMARY KEY (machine_id, validator_reference_hash),
    CONSTRAINT fk_currency_acceptance_machine
        FOREIGN KEY (machine_id)
        REFERENCES vending_machine (machine_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_currency_acceptance_reference_hash
        CHECK (validator_reference_hash ~ '^[0-9a-f]{64}$')
);
