-- Keep current behavior for existing packages while allowing OWNER to target specific customers.
ALTER TABLE script_packages
ADD COLUMN distribution_mode TEXT NOT NULL DEFAULT 'ALL_LICENSED'
CHECK (distribution_mode IN ('ALL_LICENSED', 'SELECTED_CUSTOMERS'));

CREATE TABLE IF NOT EXISTS script_package_customers (
  package_id TEXT NOT NULL REFERENCES script_packages(id) ON DELETE CASCADE,
  customer_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  granted_by TEXT NOT NULL REFERENCES users(id),
  granted_at INTEGER NOT NULL,
  PRIMARY KEY (package_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_script_package_customers_customer
ON script_package_customers(customer_id, package_id);
