ALTER TABLE reliable_event_outbox
    ADD COLUMN claim_token VARCHAR(36) NULL,
    ADD COLUMN locked_until DATETIME(3) NULL;

-- Legacy PROCESSING rows retain locked_at and are reclaimed by the normal timeout.
