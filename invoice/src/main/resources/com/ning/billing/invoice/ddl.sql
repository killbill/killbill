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
  amount_paid numeric(10,4) NOT NULL DEFAULT 0,
  last_payment_attempt datetime DEFAULT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;

CREATE INDEX invoices_account_id ON invoices(account_id ASC);

DROP TABLE IF EXISTS invoice_payments;
CREATE TABLE invoice_payments (
  id char(36) NOT NULL,
  invoice_id char(36) NOT NULL,
  payment_id char(36) NOT NULL,
  payment_date datetime NOT NULL,
  amount numeric(10,4) NOT NULL,
  currency char(3) NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX invoice_payments_unique ON invoice_payments(invoice_id, payment_id);

CREATE VIEW amount_remaining AS
SELECT invoice_items.id,
       SUM(invoice_items.amount) AS amount_owed
FROM invoice_items
GROUP BY invoice_items.id;

CREATE VIEW invoices_for_payment AS
SELECT i.id,
       DATEDIFF(NOW(), MAX(i.last_payment_attempt)) AS days_due
FROM invoices i
LEFT JOIN amount_remaining ar ON i.id = ar.id
WHERE (i.amount_paid < ar.amount_owed)
      AND (i.last_payment_attempt IS NOT NULL)
GROUP BY i.id;



