alter table monitored_service add column execution_path varchar(1024);
alter table monitored_service add column start_command varchar(4096);
update monitored_service set start_command = restart_command where start_command is null;
alter table monitored_service alter column start_command set not null;
alter table monitored_service add column manual_restart_enabled boolean default false not null;
alter table monitored_service add column last_known_pid bigint;
