
DROP TABLE IF EXISTS overdue_states;
CREATE TABLE overdue_states (
  id char(36) NOT NULL,
  state varchar(50) NOT NULL,
  type varchar(20) NOT NULL,    
  created_date datetime NOT NULL
) ENGINE=innodb;
CREATE INDEX overdue_states_by_id ON overdue_states (id);