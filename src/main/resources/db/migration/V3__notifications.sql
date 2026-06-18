alter table monitored_service add column notifications_enabled boolean default true not null;

create table app_setting (
    setting_key varchar(128) primary key,
    setting_value varchar(1024) not null
);
