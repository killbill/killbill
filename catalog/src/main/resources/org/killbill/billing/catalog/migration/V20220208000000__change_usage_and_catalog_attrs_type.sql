alter table rolled_up_usage modify amount decimal(18, 9) not null;

alter table catalog_override_block_definition modify bsize decimal(18, 9) not null;
alter table catalog_override_block_definition modify max decimal(18, 9) null;