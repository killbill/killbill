create or replace view v_pivot_date as
select distinct
  effective_date fulldate
from recognized_revenue
;

create or replace view v_past_period_rev as
select
  recognized_date
, sum(recognized_amount) recognized_amount
from recognized_revenue
where effective_date = last_day(effective_date)
group by 1
;

create or replace view v_revenue_recognition as
select
  effective_date as day
, cur_period_rev.recognized_amount + past_period_rev.recognized_amount as count
from v_pivot_date pivot_date
join recognized_revenue cur_period_rev on cur_period_rev.recognized_date = date_format(pivot_date.fulldate, '%Y-%m-01') and cur_period_rev.effective_date = pivot_date.fulldate
join v_past_period_rev past_period_rev on past_period_rev.recognized_date = date_format(pivot_date.fulldate, '%Y-%m-01')
order by effective_date
;
