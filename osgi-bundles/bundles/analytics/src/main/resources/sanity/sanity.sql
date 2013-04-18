-- bac

-- A1a
select a.updated_date, bac.updated_date
from accounts a
 left outer join bac on a.id = bac.account_id
where a.record_id != bac.account_record_id
      or ( coalesce(a.id , '') != coalesce(bac.account_id ,''))
      or a.external_key !=  bac.account_external_key
      or ( coalesce(a.email , '') != coalesce(bac.email ,''))
      or ( coalesce(a.name , '') != coalesce(bac.account_name ,''))
      or ( coalesce(a.first_name_length , '') != coalesce(bac.first_name_length ,''))
      or ( coalesce(a.currency , '') != coalesce(bac.currency ,''))
      or ( coalesce(a.billing_cycle_day_local , '') != coalesce(bac.billing_cycle_day_local ,''))
      or ( coalesce(a.payment_method_id , '') != coalesce(bac.payment_method_id ,''))
      or ( coalesce(a.time_zone , '') != coalesce(bac.time_zone ,''))
      or ( coalesce(a.locale , '') != coalesce(bac.locale ,''))
      or ( coalesce(a.address1 , '') != coalesce(bac.address1 ,''))
      or ( coalesce(a.address2 , '') != coalesce(bac.address2 ,''))
      or ( coalesce(a.company_name , '') != coalesce(bac.company_name ,''))
      or ( coalesce(a.city , '') != coalesce(bac.city ,''))
      or ( coalesce(a.state_or_province , '') != coalesce(bac.state_or_province ,''))
      or ( coalesce(a.country , '') != coalesce(bac.country ,''))
      or ( coalesce(a.postal_code , '') != coalesce(bac.postal_code ,''))
      or ( coalesce(a.phone , '') != coalesce(bac.phone ,''))
      or ( coalesce(a.migrated , '') != coalesce(bac.migrated ,''))
      or ( coalesce(a.is_notified_for_invoices , '') != coalesce(bac.notified_for_invoices ,''))
      or a.created_date  != bac.created_date
      or a.created_by != bac.created_by
      -- RI-1967 or a.updated_date != bac.updated_date
      -- or a.updated_by != bac.updated_by
      -- RI-1966 or a.tenant_record_id != bac.tenant_record_id

-- A1b
select a.updated_date, bac.updated_date
from  bac
 left outer join accounts a on a.id = bac.account_id
where a.record_id != bac.account_record_id
      or ( coalesce(a.id , '') != coalesce(bac.account_id ,''))
      or a.external_key , '') != bac.account_external_key
      or ( coalesce(a.email , '') != coalesce(bac.email ,''))
      or ( coalesce(a.name , '') != coalesce(bac.account_name ,''))
      or ( coalesce(a.first_name_length , '') != coalesce(bac.first_name_length ,''))
      or ( coalesce(a.currency , '') != coalesce(bac.currency ,''))
      or ( coalesce(a.billing_cycle_day_local , '') != coalesce(bac.billing_cycle_day_local ,''))
      or ( coalesce(a.payment_method_id , '') != coalesce(bac.payment_method_id ,''))
      or ( coalesce(a.time_zone , '') != coalesce(bac.time_zone ,''))
      or ( coalesce(a.locale , '') != coalesce(bac.locale ,''))
      or ( coalesce(a.address1 , '') != coalesce(bac.address1 ,''))
      or ( coalesce(a.address2 , '') != coalesce(bac.address2 ,''))
      or ( coalesce(a.company_name , '') != coalesce(bac.company_name ,''))
      or ( coalesce(a.city , '') != coalesce(bac.city ,''))
      or ( coalesce(a.state_or_province , '') != coalesce(bac.state_or_province ,''))
      or ( coalesce(a.country , '') != coalesce(bac.country ,''))
      or ( coalesce(a.postal_code , '') != coalesce(bac.postal_code ,''))
      or ( coalesce(a.phone , '') != coalesce(bac.phone ,''))
      or ( coalesce(a.migrated , '') != coalesce(bac.migrated ,''))
      or ( coalesce(a.is_notified_for_invoices , '') != coalesce(bac.notified_for_invoices ,''))
      or ( coalesce(a.created_date, '')  != ( coalesce(bac.created_date,''))
      or ( coalesce(a.created_by != ( coalesce(bac.created_by,''))
      -- RI-1967 or ( coalesce(a.updated_date, '') != ( coalesce(bac.updated_date,''))
      -- or ( coalesce(a.updated_b, '')y != ( coalesce(bac.updated_by,''))
      -- RI-1966 or ( coalesce(a.tenant_record_id, '') != ( coalesce(bac.tenant_record_id,''))

*****

-- bia

-- B1a
-- this will find things it thinks should be in bia but it's correct that they're not there
select *
from invoice_items ii
     left outer join bia b on ii.id = b.item_id
where ii.type in ('CREDIT_ADJ')
      and (( coalesce(ii.record_id, '') != coalesce(b.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(b.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(b.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(b.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(b.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(b.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(b.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(b.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(b.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(b.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(b.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(b.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(b.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(b.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(b.tenant_record_id,''))
      )

-- B1b
select *
from bia b
     left outer join invoice_items ii on ii.id = b.item_id
where ( coalesce(ii.record_id, '') != coalesce(b.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(b.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(b.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(b.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(b.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(b.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(b.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(b.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(b.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(b.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(b.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(b.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(b.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(b.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(b.tenant_record_id,''))
      or ii.type not in ('CREDIT_ADJ')

-- B2
select *
from bia b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.id , '') != coalesce(b.account_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')

-- B3
select *
from bia b
     left outer join invoices i on i.id = b.invoice_id
where coalesce(i.RECORD_ID, 'NULL') != coalesce(b.invoice_number,'NULL')
      or coalesce(i.created_date, 'NULL') != coalesce(b.invoice_created_date,'NULL')
      or coalesce(i.invoice_date, 'NULL') != coalesce(b.invoice_date,'NULL')
      or coalesce(i.target_date, 'NULL') != coalesce(b.invoice_target_date,'NULL')
      or coalesce(i.currency, 'NULL') != coalesce(b.invoice_currency,'NULL')

-- B4
select *
from bia b
     left outer join invoice_items ii on ii.id = b.item_id
     left outer join bundles bndl on ii.bundle_id = bndl.id
where coalesce(bndl.external_key, 'NULL') != coalesce(b.bundle_external_key,'NULL')

-- B5

select *
from bia b
     left outer join bin on b.invoice_id = bin.invoice_id
where b.invoice_balance != bin.balance
      or b.invoice_amount_paid != bin.amount_paid
      or b.invoice_amount_charged != bin.amount_charged
      or b.invoice_original_amount_charged != bin.original_amount_charged
      or b.invoice_amount_credited != bin.amount_credited

*****

-- bii

-- C1a
select *
from invoice_items ii
     left outer join bii on ii.id = bii.item_id
where ii.type in ('FIXED','RECURRING','EXTERNAL_CHARGE')
      and (( coalesce(ii.record_id, '') != coalesce(bii.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(bii.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(bii.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(bii.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(bii.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(bii.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(bii.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(bii.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(bii.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(bii.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(bii.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(bii.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(bii.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(bii.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(bii.tenant_record_id,''))
      )

-- C1b
select *
from bii
     left outer join invoice_items ii on ii.id = bii.item_id
where ( coalesce(ii.record_id, '') != coalesce(bii.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(bii.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(bii.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(bii.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(bii.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(bii.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(bii.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(bii.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(bii.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(bii.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(bii.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(bii.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(bii.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(bii.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(bii.tenant_record_id,''))
      or ii.type not in ('FIXED','RECURRING','EXTERNAL_CHARGE')

-- C2
select *
from bii b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.id , '') != coalesce(b.account_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')

-- C3
select *
from bii b
     left outer join invoices i on i.id = b.invoice_id
where coalesce(i.RECORD_ID, 'NULL') != coalesce(b.invoice_number,'NULL')
      or coalesce(i.created_date, 'NULL') != coalesce(b.invoice_created_date,'NULL')
      or coalesce(i.invoice_date, 'NULL') != coalesce(b.invoice_date,'NULL')
      or coalesce(i.target_date, 'NULL') != coalesce(b.invoice_target_date,'NULL')
      or coalesce(i.currency, 'NULL') != coalesce(b.invoice_currency,'NULL')

-- C4
select *
from bii b
     left outer join invoice_items ii on ii.id = b.item_id
     left outer join bundles bndl on ii.bundle_id = bndl.id
where coalesce(bndl.external_key, 'NULL') != coalesce(b.bundle_external_key,'NULL')

-- C5
select *
from bii b
     left outer join bin on b.invoice_id = bin.invoice_id
where b.invoice_balance != bin.balance
      or b.invoice_amount_paid != bin.amount_paid
      or b.invoice_amount_charged != bin.amount_charged
      or b.invoice_original_amount_charged != bin.original_amount_charged
      or b.invoice_amount_credited != bin.amount_credited

*****

-- biia

-- D1a
select *
from invoice_items ii
     left outer join biia b on ii.id = b.item_id
where ii.type in ('ITEM_ADJ')
      and (( coalesce(ii.record_id, '') != coalesce(b.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(b.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(b.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(b.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(b.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(b.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(b.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(b.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(b.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(b.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(b.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(b.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(b.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(b.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(b.tenant_record_id,''))
      )

-- D1b
select *
from biia b
     left outer join invoice_items ii on ii.id = b.item_id
where ( coalesce(ii.record_id, '') != coalesce(b.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(b.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(b.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(b.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(b.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(b.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(b.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(b.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(b.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(b.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(b.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(b.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(b.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(b.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(b.tenant_record_id,''))
      or ii.type not in ('ITEM_ADJ')

-- D2
select *
from biia b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.id , '') != coalesce(b.account_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')

-- D3
select *
from biia b
     left outer join invoices i on i.id = b.invoice_id
where coalesce(i.RECORD_ID, 'NULL') != coalesce(b.invoice_number,'NULL')
      or coalesce(i.created_date, 'NULL') != coalesce(b.invoice_created_date,'NULL')
      or coalesce(i.invoice_date, 'NULL') != coalesce(b.invoice_date,'NULL')
      or coalesce(i.target_date, 'NULL') != coalesce(b.invoice_target_date,'NULL')
      or coalesce(i.currency, 'NULL') != coalesce(b.invoice_currency,'NULL')


-- D4
select *
from biia b
     left outer join invoice_items ii on ii.id = b.item_id
     left outer join bundles bndl on ii.bundle_id = bndl.id
where coalesce(bndl.external_key, 'NULL') != coalesce(b.bundle_external_key,'NULL')

-- D5
select *
from biia b
     left outer join bin on b.invoice_id = bin.invoice_id
where b.invoice_balance != bin.balance
      or b.invoice_amount_paid != bin.amount_paid
      or b.invoice_amount_charged != bin.amount_charged
      or b.invoice_original_amount_charged != bin.original_amount_charged
      or b.invoice_amount_credited != bin.amount_credited
****

-- biic

-- E1a
select *
from invoice_items ii
     left outer join biic b on ii.id = b.item_id
where ii.type in ('CBA_ADJ')
      and (( coalesce(ii.record_id, '') != coalesce(b.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(b.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(b.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(b.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(b.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(b.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(b.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(b.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(b.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(b.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(b.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(b.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(b.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(b.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(b.tenant_record_id,''))
      )

-- E1b
select *
from biic b
     left outer join invoice_items ii on ii.id = b.item_id
where ( coalesce(ii.record_id, '') != coalesce(b.invoice_item_record_id,''))
      or ( coalesce(ii.id, '') != coalesce(b.item_id,''))
      or ( coalesce(ii.type, '') != coalesce(b.item_type,''))
      or ( coalesce(ii.invoice_id, '') != coalesce(b.invoice_id,''))
      or ( coalesce(ii.account_id , '')!= coalesce(b.account_id,''))
      or ( coalesce(ii.phase_name, '') != coalesce(b.slug,''))
      or ( coalesce(ii.start_date, '') != coalesce(b.start_date,''))
      -- old trials or ( coalesce(ii.end_date, '') != coalesce(b.end_date,''))
      or ( coalesce(ii.amount, '') != coalesce(b.amount,''))
      or ( coalesce(ii.currency, '') != coalesce(b.currency,''))
      or ( coalesce(ii.linked_item_id, '') != coalesce(b.linked_item_id,''))
      or ( coalesce(ii.created_by, '') != coalesce(b.created_by,''))
      or ( coalesce(ii.created_date, '') != coalesce(b.created_date,''))
      or ( coalesce(ii.account_record_id, '') != coalesce(b.account_record_id,''))
      -- RI-1966 or ( coalesce(ii.tenant_record_id, '') != coalesce(b.tenant_record_id,''))
      or ii.type not in ('CBA_ADJ')

-- E2
select *
from biic b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.id , '') != coalesce(b.account_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')

-- E3
select *
from biic b
     left outer join invoices i on i.id = b.invoice_id
where coalesce(i.RECORD_ID, 'NULL') != coalesce(b.invoice_number,'NULL')
      or coalesce(i.created_date, 'NULL') != coalesce(b.invoice_created_date,'NULL')
      or coalesce(i.invoice_date, 'NULL') != coalesce(b.invoice_date,'NULL')
      or coalesce(i.target_date, 'NULL') != coalesce(b.invoice_target_date,'NULL')
      or coalesce(i.currency, 'NULL') != coalesce(b.invoice_currency,'NULL')

-- E4
select *
from biic b
     left outer join invoice_items ii on ii.id = b.item_id
     left outer join bundles bndl on ii.bundle_id = bndl.id
where coalesce(bndl.external_key, 'NULL') != coalesce(b.bundle_external_key,'NULL')

-- E5
select *
from biic b
     left outer join bin on b.invoice_id = bin.invoice_id
where b.invoice_balance != bin.balance
      or b.invoice_amount_paid != bin.amount_paid
      or b.invoice_amount_charged != bin.amount_charged
      or b.invoice_original_amount_charged != bin.original_amount_charged
      or b.invoice_amount_credited != bin.amount_credited

****

-- bin

-- F1a
select *
from invoices i
     left outer join bin on i.id = bin.invoice_id
where coalesce(i.record_id, '') != coalesce(bin.invoice_record_id,'')
      or coalesce(i.record_id, '') != coalesce(bin.invoice_number,'')
      or coalesce(i.id, '') != coalesce(bin.invoice_id,'')
      or ( coalesce(i.account_id, '') != coalesce(bin.account_id,''))
      or ( coalesce(i.invoice_date, '') != coalesce(bin.invoice_date,''))
      or ( coalesce(i.target_date, '') != coalesce(bin.target_date,''))
      or ( coalesce(i.currency, '') != coalesce(bin.currency,''))
      or ( coalesce(i.created_by, '') != coalesce(bin.created_by,''))
      or ( coalesce(i.created_date, '') != coalesce( bin.created_date,''))
      or ( coalesce(i.account_record_id, '') != coalesce(bin.account_record_id,''))
      -- RI-1966 or ( coalesce(i.tenant_record_id, '') != coalesce(bin.tenant_record_id,''))

-- F1b
select *
from bin
     left outer join invoices i on i.id = bin.invoice_id
where ( coalesce(i.record_id, '') != coalesce(bin.invoice_record_id,''))
      or ( coalesce(i.id, '') != coalesce(bin.invoice_id,''))
      or ( coalesce(i.account_id, '') != coalesce(bin.account_id,''))
      or ( coalesce(i.invoice_date, '') != coalesce(bin.invoice_date,''))
      or ( coalesce(i.target_date, '') != coalesce(bin.target_date,''))
      or ( coalesce(i.currency, '') != coalesce(bin.currency,''))
      or ( coalesce(i.created_by, '') != coalesce(bin.created_by,''))
      or ( coalesce(i.created_date, '') != coalesce(bin.created_date,''))
      or ( coalesce(i.account_record_id, '') != coalesce(bin.account_record_id,''))
      -- RI-1966or ( coalesce(i.tenant_record_id, '') != coalesce(bin.tenant_record_id,''))

-- F2
select *
from bin b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.id , '') != coalesce(b.account_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')


*****

-- bip

-- G1a
select *
from invoice_payments ip
     left outer join bip on ip.id = bip.invoice_payment_id
where ( coalesce(ip.RECORD_ID, 'NULL') != coalesce(bip.invoice_payment_record_id,'NULL')
      or  coalesce(ip.ID, 'NULL') != coalesce(bip.invoice_payment_id,'NULL')
      or  coalesce(ip.invoice_id, 'NULL') != coalesce(bip.invoice_id,'NULL')
      or  coalesce(ip.type, 'NULL') != coalesce(bip.invoice_payment_type,'NULL')
      or  coalesce(ip.linked_invoice_payment_id, 'NULL') != coalesce(bip.linked_invoice_payment_id,'NULL')
      or  coalesce(ip.amount, 'NULL') != coalesce(bip.amount,'NULL')
      or  coalesce(ip.currency, 'NULL') != coalesce(bip.currency,'NULL')
      or  coalesce(ip.created_date, 'NULL') != coalesce(bip.created_date,'NULL')
      or  coalesce(ip.created_by, 'NULL') != coalesce(bip.created_by,'NULL')
      or  coalesce(ip.account_record_id, 'NULL') != coalesce(bip.account_record_id,'NULL')
      -- or  coalesce(ip.tenant_record_id, 'NULL') != coalesce(bip.tenant_record_id,'NULL')
     )
      and ip.type = 'ATTEMPT'

-- G1b

select *
from bip
     left outer join invoice_payments ip on ip.id = bip.invoice_payment_id
where coalesce(ip.RECORD_ID, 'NULL') != coalesce(bip.invoice_payment_record_id,'NULL')
      or  coalesce(ip.ID, 'NULL') != coalesce(bip.invoice_payment_id,'NULL')
      or  coalesce(ip.invoice_id, 'NULL') != coalesce(bip.invoice_id,'NULL')
      or  coalesce(ip.type, 'NULL') != coalesce(bip.invoice_payment_type,'NULL')
      or  coalesce(ip.linked_invoice_payment_id, 'NULL') != coalesce(bip.linked_invoice_payment_id,'NULL')
      or  coalesce(ip.amount, 'NULL') != coalesce(bip.amount,'NULL')
      or  coalesce(ip.currency, 'NULL') != coalesce(bip.currency,'NULL')
      or  coalesce(ip.created_date, 'NULL') != coalesce(bip.created_date,'NULL')
      or  coalesce(ip.created_by, 'NULL') != coalesce(bip.created_by,'NULL')
      or  coalesce(ip.account_record_id, 'NULL') != coalesce(bip.account_record_id,'NULL')
      -- or  coalesce(ip.tenant_record_id, 'NULL') != coalesce(bip.tenant_record_id,'NULL')
      or  bip.invoice_payment_type != 'ATTEMPT'

-- G2
select *
from bip b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')

-- G3
select *
from bip b
     left outer join invoices i on i.id = b.invoice_id
where coalesce(i.RECORD_ID, 'NULL') != coalesce(b.invoice_number,'NULL')
      or coalesce(i.created_date, 'NULL') != coalesce(b.invoice_created_date,'NULL')
      or coalesce(i.invoice_date, 'NULL') != coalesce(b.invoice_date,'NULL')
      or coalesce(i.target_date, 'NULL') != coalesce(b.invoice_target_date,'NULL')
      or coalesce(i.currency, 'NULL') != coalesce(b.invoice_currency,'NULL')

-- G4
select *
from bip b
     left outer join bin on b.invoice_id = bin.invoice_id
where b.invoice_balance != bin.balance
      or b.invoice_amount_paid != bin.amount_paid
      or b.invoice_amount_charged != bin.amount_charged
      or b.invoice_original_amount_charged != bin.original_amount_charged
      or b.invoice_amount_credited != bin.amount_credited

-- G5
select *
from bip
     left outer join invoice_payments ip on bip.invoice_payment_id = ip.id
     left outer join payments p on ip.payment_id = p.id
where coalesce(p.RECORD_ID, 'NULL') != coalesce(bip.payment_number,'NULL')

*****

-- bipr
-- H1a
select *
from invoice_payments ip
     left outer join bipr on ip.id = bipr.invoice_payment_id
where ( coalesce(ip.RECORD_ID, 'NULL') != coalesce(bipr.invoice_payment_record_id,'NULL')
      or  coalesce(ip.ID, 'NULL') != coalesce(bipr.invoice_payment_id,'NULL')
      or  coalesce(ip.invoice_id, 'NULL') != coalesce(bipr.invoice_id,'NULL')
      or  coalesce(ip.type, 'NULL') != coalesce(bipr.invoice_payment_type,'NULL')
      or  coalesce(ip.linked_invoice_payment_id, 'NULL') != coalesce(bipr.linked_invoice_payment_id,'NULL')
      or  coalesce(ip.amount, 'NULL') != coalesce(bipr.amount,'NULL')
      or  coalesce(ip.currency, 'NULL') != coalesce(bipr.currency,'NULL')
      or  coalesce(ip.created_date, 'NULL') != coalesce(bipr.created_date,'NULL')
      or  coalesce(ip.created_by, 'NULL') != coalesce(bipr.created_by,'NULL')
      or  coalesce(ip.account_record_id, 'NULL') != coalesce(bipr.account_record_id,'NULL')
      -- or  coalesce(ip.tenant_record_id, 'NULL') != coalesce(bipr.tenant_record_id,'NULL')
     )
      and ip.type = 'REFUND' -- ?

-- H1b

select *
from bipr
     left outer join invoice_payments ip on ip.id = bipr.invoice_payment_id
where coalesce(ip.RECORD_ID, 'NULL') != coalesce(bipr.invoice_payment_record_id,'NULL')
      or  coalesce(ip.ID, 'NULL') != coalesce(bipr.invoice_payment_id,'NULL')
      or  coalesce(ip.invoice_id, 'NULL') != coalesce(bipr.invoice_id,'NULL')
      or  coalesce(ip.type, 'NULL') != coalesce(bipr.invoice_payment_type,'NULL')
      or  coalesce(ip.linked_invoice_payment_id, 'NULL') != coalesce(bipr.linked_invoice_payment_id,'NULL')
      or  coalesce(ip.amount, 'NULL') != coalesce(bipr.amount,'NULL')
      or  coalesce(ip.currency, 'NULL') != coalesce(bipr.currency,'NULL')
      or  coalesce(ip.created_date, 'NULL') != coalesce(bipr.created_date,'NULL')
      or  coalesce(ip.created_by, 'NULL') != coalesce(bipr.created_by,'NULL')
      or  coalesce(ip.account_record_id, 'NULL') != coalesce(bipr.account_record_id,'NULL')
      -- or  coalesce(ip.tenant_record_id, 'NULL') != coalesce(bipr.tenant_record_id,'NULL')
      or  bipr.invoice_payment_type != 'REFUND' -- ?

-- H2
select *
from bipr b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')

-- H3
select *
from bipr b
     left outer join invoices i on i.id = b.invoice_id
where coalesce(i.RECORD_ID, 'NULL') != coalesce(b.invoice_number,'NULL')
      or coalesce(i.created_date, 'NULL') != coalesce(b.invoice_created_date,'NULL')
      or coalesce(i.invoice_date, 'NULL') != coalesce(b.invoice_date,'NULL')
      or coalesce(i.target_date, 'NULL') != coalesce(b.invoice_target_date,'NULL')
      or coalesce(i.currency, 'NULL') != coalesce(b.invoice_currency,'NULL')

-- H4
select *
from bipr b
     left outer join bin on b.invoice_id = bin.invoice_id
where b.invoice_balance != bin.balance
      or b.invoice_amount_paid != bin.amount_paid
      or b.invoice_amount_charged != bin.amount_charged
      or b.invoice_original_amount_charged != bin.original_amount_charged
      or b.invoice_amount_credited != bin.amount_credited

-- H5
select *
from bip
     left outer join invoice_payments ip on bip.invoice_payment_id = ip.id
     left outer join payments p on ip.payment_id = p.id
where coalesce(p.RECORD_ID, 'NULL') != coalesce(bip.payment_number,'NULL')


*****

-- bos

*****

-- bst

select se.plan_name, se.phase_name, bst.next_slug
from bst
     left outer join subscription_events se on bst.subscription_event_record_id = se.record_id
where 1=0
      or se.requested_date != bst.requested_timestamp
      or se.effective_date != bst.next_start_date
      or se.subscription_id!= bst.subscription_id
      or se.phase_name!= bst.next_slug
      or se.price_list_name!= bst.next_price_list
      or se.created_by!= bst.created_by
      or se.created_date!= bst.created_date
      or se.account_record_id!= bst.account_record_id
      -- RI-1966 or se.tenant_record_id!= bst.tenant_record_id

select *
from bst  b
     left outer join accounts a on a.id = b.account_id
where coalesce(a.record_id )!=  coalesce(b.account_record_id ,'')
      or coalesce(a.id , '') != coalesce(b.account_id ,'')
      or coalesce(a.external_key, '') != coalesce(b.account_external_key,'')
      or coalesce(a.name , '') != coalesce(b.account_name ,'')
