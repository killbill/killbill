DROP TABLE IF EXISTS payment_attempts;
CREATE TABLE payment_attempts (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    invoice_id char(36) COLLATE utf8_bin NOT NULL,
    amount decimal(8,2),
    currency char(3),
    payment_attempt_date datetime NOT NULL,
    payment_id char(36) COLLATE utf8_bin,
    retry_count tinyint,
    processing_status varchar(20),    
    invoice_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_attempts_id ON payment_attempts(id);
CREATE INDEX payment_attempts_account_id_invoice_id ON payment_attempts(account_id, invoice_id);

DROP TABLE IF EXISTS payment_attempt_history;
CREATE TABLE payment_attempt_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    invoice_id char(36) COLLATE utf8_bin NOT NULL,
    amount decimal(8,2),
    currency char(3),
    payment_attempt_date datetime NOT NULL,
    payment_id char(36) COLLATE utf8_bin,
    retry_count tinyint,
    processing_status varchar(20),        
    invoice_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX payment_attempt_history_record_id ON payment_attempt_history(record_id);

DROP TABLE IF EXISTS payments; 
CREATE TABLE payments (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    external_payment_id varchar(36) COLLATE utf8_bin NOT NULL,
    amount decimal(8,2),
    refund_amount decimal(8,2),
    payment_number varchar(36) COLLATE utf8_bin,
    bank_identification_number varchar(36) COLLATE utf8_bin,
    status varchar(20) COLLATE utf8_bin,
    reference_id varchar(36) COLLATE utf8_bin,
    payment_type varchar(20) COLLATE utf8_bin,
    payment_method_id varchar(36) COLLATE utf8_bin,
    payment_method varchar(20) COLLATE utf8_bin,
    card_type varchar(20) COLLATE utf8_bin,
    card_country varchar(50) COLLATE utf8_bin,
    effective_date datetime,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payments_id ON payments(id);

DROP TABLE IF EXISTS payment_history;
CREATE TABLE payment_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    external_payment_id varchar(36) COLLATE utf8_bin NOT NULL,
    amount decimal(8,2),
    refund_amount decimal(8,2),
    payment_number varchar(36) COLLATE utf8_bin,
    bank_identification_number varchar(36) COLLATE utf8_bin,
    status varchar(20) COLLATE utf8_bin,
    reference_id varchar(36) COLLATE utf8_bin,
    payment_type varchar(20) COLLATE utf8_bin,
    payment_method_id varchar(36) COLLATE utf8_bin,
    payment_method varchar(20) COLLATE utf8_bin,
    card_type varchar(20) COLLATE utf8_bin,
    card_country varchar(50) COLLATE utf8_bin,
    effective_date datetime,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX payment_history_record_id ON payment_history(record_id);
