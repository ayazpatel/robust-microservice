-- Ensure message_status allows RECEIVED in addition to SENT/FAILED/PENDING
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'messages_message_status_check'
          AND table_name = 'messages'
    ) THEN
        ALTER TABLE messages DROP CONSTRAINT messages_message_status_check;
    END IF;
END $$;

ALTER TABLE messages
    ADD CONSTRAINT messages_message_status_check
    CHECK (message_status IN ('SENT','RECEIVED','FAILED','PENDING'));

