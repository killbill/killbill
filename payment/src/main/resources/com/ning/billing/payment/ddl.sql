DROP TABLE IF EXISTS payment_attempts;
CREATE TABLE payment_attempts (
      payment_attempt_id char(36) COLLATE utf8_bin NOT NULL,
      account_id char(36) COLLATE utf8_bin NOT NULL,
      invoice_id char(36) COLLATE utf8_bin NOT NULL,
      amount decimal(8,2),
      currency char(3),
      payment_attempt_dt datetime NOT NULL,
      payment_id varchar(36) COLLATE utf8_bin,
      retry_count tinyint,
      next_retry_dt datetime,
      invoice_dt datetime NOT NULL,
      created_dt datetime NOT NULL,
      updated_dt datetime NOT NULL,
      PRIMARY KEY (payment_attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

DROP TABLE IF EXISTS payments; 
CREATE TABLE payments (
      payment_id varchar(36) COLLATE utf8_bin NOT NULL,
      amount decimal(8,2),
      refund_amount decimal(8,2),
      payment_number varchar(36) COLLATE utf8_bin,
      bank_identification_number varchar(36) COLLATE utf8_bin,
      status varchar(20) COLLATE utf8_bin,
      reference_id varchar(36) COLLATE utf8_bin,
      payment_type varchar(20) COLLATE utf8_bin,
      payment_method_id varchar(20) COLLATE utf8_bin,
      payment_method varchar(20) COLLATE utf8_bin,
      card_type varchar(20) COLLATE utf8_bin,
      card_country varchar(50) COLLATE utf8_bin,
      effective_dt datetime,
      created_dt datetime NOT NULL,
      updated_dt datetime NOT NULL,
      PRIMARY KEY (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
