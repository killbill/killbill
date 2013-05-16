create or replace view v_new_accounts_per_day as
select
  date_format(created_date, '%Y-%m-%d') as day
, count(*) as count
from bac
where created_date > date_sub(curdate(), interval 90 day)
group by 1
order by 1 asc
;
