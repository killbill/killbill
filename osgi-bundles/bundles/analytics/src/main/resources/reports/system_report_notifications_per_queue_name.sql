create or replace view v_system_report_notifications_per_queue_name as
select
  queue_name as pivot
, date_format(effective_date, '%Y-%m-%d') as day
, count(*) as count
from notifications
where processing_state = 'AVAILABLE'
group by 1, 2
order by 1, 2 asc
;
