alter table invoice_items add column quantity int after linked_item_id;
alter table invoice_items add column item_details text after quantity;