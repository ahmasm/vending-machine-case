CREATE TABLE processed_command (
    machine_id text NOT NULL,
    idempotency_key text NOT NULL,
    request_hash varchar(64) NOT NULL,
    result_code varchar(64),
    result_payload jsonb,
    completed_at timestamp with time zone,
    CONSTRAINT pk_processed_command PRIMARY KEY (machine_id, idempotency_key),
    CONSTRAINT fk_processed_command_machine
        FOREIGN KEY (machine_id)
        REFERENCES vending_machine (machine_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_processed_command_key_not_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_processed_command_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_processed_command_result_complete CHECK (
        (result_code IS NULL AND result_payload IS NULL AND completed_at IS NULL)
        OR
        (result_code IS NOT NULL AND result_payload IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_processed_command_result_payload_object CHECK (
        result_payload IS NULL OR jsonb_typeof(result_payload) = 'object'
    )
);
