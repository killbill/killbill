
DROP TABLE IF EXISTS payments; 
CREATE TABLE payments (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    invoice_id char(36) COLLATE utf8_bin NOT NULL,
    payment_method_id char(36) COLLATE utf8_bin NOT NULL,    
    amount decimal(8,2),
    currency char(3),    
    effective_date datetime,
    payment_status varchar(32),  
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
    account_id char(36) COLLATE utf8_bin NOT NULL,
    invoice_id char(36) COLLATE utf8_bin NOT NULL,
    payment_method_id char(36) COLLATE utf8_bin NOT NULL,    
    amount decimal(8,2),
    currency char(3),    
    effective_date datetime,
    payment_status varchar(32), 
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX payment_history_record_id ON payment_history(record_id);


DROP TABLE IF EXISTS payment_attempts;
CREATE TABLE payment_attempts (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    payment_id char(36) COLLATE utf8_bin NOT NULL,
    payment_error varchar(256),              
    processing_status varchar(20),        
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_attempts_id ON payment_attempts(id);


DROP TABLE IF EXISTS payment_attempt_history;
CREATE TABLE payment_attempt_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    payment_id char(36) COLLATE utf8_bin NOT NULL,
    payment_error varchar(256),              
    processing_status varchar(20),        
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
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
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_methods_id ON payment_methods(id);


DROP TABLE IF EXISTS payment_method_history;
CREATE TABLE payment_method_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    account_id char(36) COLLATE utf8_bin NOT NULL,
    plugin_name varchar(20) DEFAULT NULL, 
    is_active bool DEFAULT true,              
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY (history_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE UNIQUE INDEX payment_method_history_record_id ON payment_method_history(record_id);





