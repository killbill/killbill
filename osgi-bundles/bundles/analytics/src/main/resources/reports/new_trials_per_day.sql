create or replace view v_new_trials_per_day as
select
  date_format(next_start_date, '%Y-%m-%d') as day
, count(*) as count
from bst
where event = 'ADD_BASE'
and next_phase = 'TRIAL'
and report_group = 'default'
group by 1
order by 1 asc
;
