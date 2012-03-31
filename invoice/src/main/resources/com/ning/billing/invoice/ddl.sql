DROP TABLE IF EXISTS invoice_items;
DROP TABLE IF EXISTS recurring_invoice_items;
CREATE TABLE recurring_invoice_items (
  id char(36) NOT NULL,
  invoice_id char(36) NOT NULL,
  account_id char(36) NOT NULL,
  subscription_id char(36) NOT NULL,
  plan_name varchar(50) NOT NULL,
  phase_name varchar(50) NOT NULL,
  start_date datetime NOT NULL,
  end_date datetime NOT NULL,
  amount numeric(10,4) NULL,
  rate numeric(10,4) NULL,
  currency char(3) NOT NULL,
  reversed_item_id char(36),
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX recurring_invoice_items_subscription_id ON recurring_invoice_items(subscription_id ASC);
CREATE INDEX recurring_invoice_items_invoice_id ON recurring_invoice_items(invoice_id ASC);

DROP TABLE IF EXISTS fixed_invoice_items;
CREATE TABLE fixed_invoice_items (
  id char(36) NOT NULL,
  invoice_id char(36) NOT NULL,
  account_id char(36) NOT NULL,
  subscription_id char(36) NOT NULL,
  plan_name varchar(50) NOT NULL,
  phase_name varchar(50) NOT NULL,
  start_date datetime NOT NULL,
  end_date datetime NOT NULL,
  amount numeric(10,4) NULL,
  currency char(3) NOT NULL,
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX fixed_invoice_items_subscription_id ON fixed_invoice_items(subscription_id ASC);
CREATE INDEX fixed_invoice_items_invoice_id ON fixed_invoice_items(invoice_id ASC);

DROP TABLE IF EXISTS invoice_locking;

DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
  invoice_number int NOT NULL AUTO_INCREMENT,
  id char(36) NOT NULL,
  account_id char(36) NOT NULL,
  invoice_date datetime NOT NULL,
  target_date datetime NOT NULL,
  currency char(3) NOT NULL,
  migrated bool NOT NULL,
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  PRIMARY KEY(invoice_number)
) ENGINE=innodb;
CREATE INDEX invoices_invoice_number ON invoices(invoice_number ASC);
CREATE INDEX invoices_id ON invoices(id ASC);
CREATE INDEX invoices_account_id ON invoices(account_id ASC);

DROP TABLE IF EXISTS invoice_payments;
CREATE TABLE invoice_payments (
  id char(36) NOT NULL,
  invoice_id char(36) NOT NULL,
  payment_attempt_id char(36) COLLATE utf8_bin NOT NULL,
  payment_attempt_date datetime,
  amount numeric(10,4),
  currency char(3),
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  PRIMARY KEY(invoice_id, payment_attempt_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX invoice_payments_unique ON invoice_payments(invoice_id, payment_attempt_id);

DROP VIEW IF EXISTS invoice_payment_summary;
CREATE VIEW invoice_payment_summary AS
SELECT invoice_id,
       CASE WHEN SUM(amount) IS NULL THEN 0 ELSE SUM(amount) END AS total_paid,
       MAX(payment_attempt_date) AS last_payment_date
FROM invoice_payments
GROUP BY invoice_id;

DROP VIEW IF EXISTS invoice_item_summary;
CREATE VIEW invoice_item_summary AS
SELECT i.id as invoice_id, 
    CASE WHEN SUM(rii.amount) IS NULL THEN 0 ELSE SUM(rii.amount) END 
    + CASE WHEN SUM(fii.amount) IS NULL THEN 0 ELSE SUM(fii.amount) END AS amount_invoiced
FROM invoices i 
LEFT JOIN recurring_invoice_items rii ON i.id = rii.invoice_id
LEFT JOIN fixed_invoice_items fii ON i.id = fii.invoice_id
GROUP BY invoice_id;

