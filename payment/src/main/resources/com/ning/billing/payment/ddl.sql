

DROP TABLE IF EXISTS payments; 
CREATE TABLE payments (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    invoice_id char(36) COLLATE utf8_bin NOT NULL,
    payment_method_id char(36) COLLATE utf8_bin NOT NULL,    
    amount numeric(10,4),
    currency char(3),    
    effective_date datetime,
    payment_status varchar(50),
    ext_first_payment_ref_id varchar(128),
    ext_second_payment_ref_id varchar(128),    
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payments_id ON payments(id);
CREATE INDEX payments_inv ON payments(invoice_id);
CREATE INDEX payments_accnt ON payments(account_id);

DROP TABLE IF EXISTS payment_history; 
CREATE TABLE payment_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,    
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    invoice_id char(36) COLLATE utf8_bin NOT NULL,
    payment_method_id char(36) COLLATE utf8_bin NOT NULL,    
    amount numeric(10,4),
    currency char(3),    
    effective_date datetime,
    payment_status varchar(50),
    ext_first_payment_ref_id varchar(128),
    ext_second_payment_ref_id varchar(128),    
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX payment_history_record_id ON payment_history(record_id);


DROP TABLE IF EXISTS payment_attempts;
CREATE TABLE payment_attempts (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    payment_id char(36) COLLATE utf8_bin NOT NULL,
    gateway_error_code varchar(32),              
    gateway_error_msg varchar(256),
    processing_status varchar(50),
    requested_amount numeric(10,4),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_attempts_id ON payment_attempts(id);
CREATE INDEX payment_attempts_payment ON payment_attempts(payment_id);


DROP TABLE IF EXISTS payment_attempt_history;
CREATE TABLE payment_attempt_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    payment_id char(36) COLLATE utf8_bin NOT NULL,
    gateway_error_code varchar(32),              
    gateway_error_msg varchar(256),
    processing_status varchar(50),
    requested_amount numeric(10,4),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX payment_attempt_history_record_id ON payment_attempt_history(record_id);


DROP TABLE IF EXISTS payment_methods;
CREATE TABLE payment_methods (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    plugin_name varchar(20) DEFAULT NULL,
    is_active bool DEFAULT true, 
    external_id varchar(64), 
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_methods_id ON payment_methods(id);
CREATE INDEX payment_methods_active_accnt ON payment_methods(is_active, account_id);


DROP TABLE IF EXISTS payment_method_history;
CREATE TABLE payment_method_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    plugin_name varchar(20) DEFAULT NULL, 
    is_active bool DEFAULT true, 
    external_id varchar(64),                  
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_method_history_record_id ON payment_method_history(record_id);

DROP TABLE IF EXISTS refunds; 
CREATE TABLE refunds (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    payment_id char(36) COLLATE utf8_bin NOT NULL,    
    amount numeric(10,4),
    currency char(3),   
    is_adjusted tinyint(1),
    refund_status varchar(50), 
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX refunds_id ON refunds(id);
CREATE INDEX refunds_pay ON refunds(payment_id);
CREATE INDEX refunds_accnt ON refunds(account_id);

DROP TABLE IF EXISTS refund_history; 
CREATE TABLE refund_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT, 
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    payment_id char(36) COLLATE utf8_bin NOT NULL,    
    amount numeric(10,4),
    currency char(3),   
    is_adjusted tinyint(1),
    refund_status varchar(50), 
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX refund_history_record_id ON refund_history(record_id);






