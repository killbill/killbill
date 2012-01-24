DROP TABLE IF EXISTS invoice_items;
CREATE TABLE invoice_items (
  id char(36) NOT NULL,
  invoice_id char(36) NOT NULL,
  subscription_id char(36) NOT NULL,
  start_date datetime NOT NULL,
  end_date datetime NOT NULL,
  description varchar(100) NOT NULL,
  amount numeric(10,4) NOT NULL,
  rate numeric(10,4) NOT NULL,
  currency char(3) NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;

CREATE INDEX invoice_items_subscription_id ON invoice_items(subscription_id ASC);

DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
  id char(36) NOT NULL,
  account_id char(36) NOT NULL,
  invoice_date datetime NOT NULL,
  target_date datetime NOT NULL,
  currency char(3) NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;

CREATE INDEX invoices_account_id ON invoices(account_id ASC);

DROP TABLE IF EXISTS invoice_payments;
CREATE TABLE invoice_payments (
  invoice_id char(36) NOT NULL,
  payment_attempt_id char(36) COLLATE utf8_bin NOT NULL,
  payment_attempt_date datetime,
  amount numeric(10,4),
  currency char(3),
  created_date datetime NOT NULL,
  updated_date datetime NOT NULL,
  PRIMARY KEY(invoice_id, payment_attempt_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX invoice_payments_unique ON invoice_payments(invoice_id, payment_attempt_id);

DROP VIEW IF EXISTS invoice_payment_summary;
CREATE VIEW invoice_payment_summary AS
SELECT invoice_id, SUM(amount) AS total_paid, MAX(payment_attempt_date) AS last_payment_date
FROM invoice_payments
GROUP BY invoice_id;

DROP VIEW IF EXISTS invoice_item_summary;
CREATE VIEW invoice_item_summary AS
select invoice_id, sum(amount) AS total_amount
from invoice_items
group by invoice_id;
