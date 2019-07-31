drop index tag_history_by_object on tags;
create index tag_history_by_object on tag_history(object_id);
