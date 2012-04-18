
DROP TABLE IF EXISTS blocking_states;
CREATE TABLE blocking_states (
  id char(36) NOT NULL,
  type varchar(20) NOT NULL,
  state varchar(50) NOT NULL,  
  service varchar(20) NOT NULL,    
  block_change bool NOT NULL,
  block_entitlement bool NOT NULL,
  block_billing bool NOT NULL,
  created_date datetime NOT NULL
) ENGINE=innodb;
CREATE INDEX blocking_states_by_id ON blocking_states (id);