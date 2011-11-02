DROP TABLE IF EXISTS invoice_items;
CREATE TABLE invoice_items (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  invoice_item_id char(36) NOT NULL,
  invoice_id char(36) NOT NULL,
  subscription_id char(36) NOT NULL,
  start_date datetime NOT NULL,
  end_date datetime NOT NULL,
  description varchar(100) NOT NULL,
  amount numeric(10,4) NOT NULL,
  rate numeric(10,4) NOT NULL,
  currency varchar(5) NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;

CREATE INDEX invoice_items_subscription_id ON invoice_items(subscription_id ASC);

DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
  id int(11) unsigned NOT NULL AUTO_INCREMENT,
  invoice_id char(36) NOT NULL,
  account_id char(36) NOT NULL,
  invoice_date datetime NOT NULL,
  amount_paid numeric(10,4) NOT NULL DEFAULT 0,
  amount_outstanding numeric(10,4) NOT NULL,
  last_payment_attempt datetime DEFAULT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;

CREATE INDEX invoices_account_id ON invoices(account_id ASC);
CREATE INDEX invoices_invoice_id ON invoices(invoice_id ASC);



