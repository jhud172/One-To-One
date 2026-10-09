-- Existing orders keep a null key; never infer a historic checkout identity.
ALTER TABLE merch_orders ADD COLUMN IF NOT EXISTS checkout_key VARCHAR(36);
ALTER TABLE merch_orders ADD COLUMN IF NOT EXISTS checkout_currency VARCHAR(3);
ALTER TABLE merch_orders ADD COLUMN IF NOT EXISTS checkout_success_url VARCHAR(2048);
ALTER TABLE merch_orders ADD COLUMN IF NOT EXISTS checkout_cancel_url VARCHAR(2048);
CREATE UNIQUE INDEX IF NOT EXISTS uq_merch_order_user_checkout
    ON merch_orders (user_id, checkout_key);
