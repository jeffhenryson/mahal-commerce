ALTER TABLE campaign_automations
    ADD COLUMN webhook_url     VARCHAR(500),
    ADD COLUMN webhook_headers TEXT;

ALTER TABLE campaign_log
    ADD COLUMN erro_detalhe VARCHAR(500);
