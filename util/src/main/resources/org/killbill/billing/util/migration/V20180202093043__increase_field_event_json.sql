alter table bus_events modify event_json text not null;
alter table bus_events_history modify event_json text not null;
alter table notifications modify event_json text not null;
alter table notifications_history modify event_json text not null;
