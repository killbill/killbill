CREATE DATABASE IF NOT EXISTS killbill;

USE killbill;

DROP TABLE IF EXISTS events;
CREATE TABLE events (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    event_id char(36) NOT NULL,
    event_type varchar(9) NOT NULL,
    user_type varchar(10) DEFAULT NULL, 
    created_dt datetime NOT NULL,
    updated_dt datetime NOT NULL,    
    requested_dt datetime NOT NULL,
    effective_dt datetime NOT NULL,   
    subscription_id char(36) NOT NULL,
    plan_name varchar(64) DEFAULT NULL,    
    phase_name varchar(128) DEFAULT NULL,
    plist_name varchar(64) DEFAULT NULL,    
    current_version int(11) DEFAULT 1,
    is_active bool DEFAULT 1,
    processing_owner char(36) DEFAULT NULL,
    processing_available_dt datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    PRIMARY KEY(id)
) ENGINE=innodb;

DROP TABLE IF EXISTS claimed_events;
CREATE TABLE claimed_events (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,    
    sequence_id int(11) unsigned NOT NULL,    
    owner_id char(36) NOT NULL,
    hostname varchar(64) NOT NULL,
    claimed_dt datetime NOT NULL,
    event_id char(36) NOT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;
  

DROP TABLE IF EXISTS subscriptions;
CREATE TABLE subscriptions (
    id char(36) NOT NULL,
    bundle_id char(36) NOT NULL,
    category varchar(32) NOT NULL,
    start_dt datetime NOT NULL,
    bundle_start_dt datetime NOT NULL,    
    active_version int(11) DEFAULT 1,   
    ctd_dt datetime DEFAULT NULL,
    ptd_dt datetime DEFAULT NULL,
    PRIMARY KEY(id)        
) ENGINE=innodb;
 
DROP TABLE IF EXISTS bundles;
CREATE TABLE bundles (
    id char(36) NOT NULL,
    start_dt datetime, /*NOT NULL*/
    name varchar(64) NOT NULL,
    account_id char(36) NOT NULL,
    PRIMARY KEY(id)        
) ENGINE=innodb;

