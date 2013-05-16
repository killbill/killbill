create or replace view v_conversions_per_day as
select
  date_format(next_start_date, '%Y-%m-%d') as day
, count(*) as count
from bst
where next_start_date > date_sub(curdate(), interval 90 day)
and next_start_date <= curdate()
and event = 'SYSTEM_CHANGE_BASE'
and prev_phase = 'TRIAL'
and next_phase = 'EVERGREEN'
group by 1
order by 1 asc
;
