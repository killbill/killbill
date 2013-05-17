create or replace view v_new_trials_last_24_hours as
select
  next_slug as pivot
, date_format(next_start_date, '%Y-%m-%dT%H:00:00Z') as day
, count(*) as count
from bst
where next_start_date > date_sub(curdate(), interval 24 hour)
and next_start_date <= curdate()
and event = 'ADD_BASE'
and next_phase = 'TRIAL'
and report_group = 'default'
group by 1, 2
order by 1, 2 asc
;
