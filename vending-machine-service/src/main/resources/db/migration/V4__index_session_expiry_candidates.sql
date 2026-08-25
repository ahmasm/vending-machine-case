CREATE INDEX ix_purchase_session_expiry_candidates
    ON purchase_session (last_activity_at, machine_id, session_id)
    WHERE status = 'ACTIVE';
