alter table tag_definitions add column applicable_object_types varchar(500) after name;
alter table tag_definition_history add column applicable_object_types varchar(500) after name;
