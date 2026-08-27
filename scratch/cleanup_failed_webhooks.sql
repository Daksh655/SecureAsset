-- DEVELOPMENT-ONLY CLEANUP
-- Run this against your local PostgreSQL database to remove stuck webhook events
-- caused by the rolled-back demo transactions.

DELETE FROM processed_webhook_events
WHERE event_id IN (
    -- You can replace this with specific known failed event IDs if you have them,
    -- or query for events that have no corresponding recovery action update.
    -- Assuming your failed test events were prefixed or known.
    -- If you just want to clear recent test events:
    SELECT event_id FROM processed_webhook_events
    WHERE processed_at > CURRENT_DATE - INTERVAL '1 day'
    -- AND event_id = 'YOUR_FAILED_EVENT_ID'
);
