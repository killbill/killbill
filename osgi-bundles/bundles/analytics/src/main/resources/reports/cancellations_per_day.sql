create or replace view v_cancellations_per_day as
select
  prev_phase as pivot
, date_format(next_start_date, '%Y-%m-%d') as day
, count(*) as count
from bst
where event = 'CANCEL_BASE'
and report_group = 'default'
group by 1, 2
order by 1, 2 asc
;
