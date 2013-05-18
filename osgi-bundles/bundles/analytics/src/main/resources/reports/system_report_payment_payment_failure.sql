create or replace view v_system_report_payment_payment_failure as
select
  date_format(updated_date, '%Y-%m-%d') as day
, count(*) as count
from payments
join bin using(invoice_id)
where bin.balance > 0
and payment_status = 'PAYMENT_FAILURE'
group by date_format(updated_date, '%Y-%m-%d')
order by date_format(updated_date, '%Y-%m-%d') asc
;
