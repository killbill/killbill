create or replace view v_new_trials_per_day as
select
  date_format(next_start_date, '%Y-%m-%d') as day
, count(*) as count
from bst
where next_start_date > date_sub(curdate(), interval 90 day)
and event = 'ADD_BASE'
group by 1
order by 1 asc
;
