create or replace view v_invoices_per_day as
select
  currency as pivot
, date_format(created_date, '%Y-%m-%d') as day
, sum(original_amount_charged) as count
from bin
where report_group = 'default'
group by 1, 2
order by 1, 2 asc
;
