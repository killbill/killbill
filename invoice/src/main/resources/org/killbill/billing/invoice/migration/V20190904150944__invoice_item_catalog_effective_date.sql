alter table invoice_items add column catalog_effective_date datetime after usage_name;
alter table invoice_item_history add column catalog_effective_date datetime after usage_name;
