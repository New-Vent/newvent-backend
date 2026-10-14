-- 관리자가 이벤트에 올린 이미지. 파일은 디스크(AssetStore)에 있고 여기는 자리표다.
create table event_assets (
    id           varchar(32)  primary key,
    event_id     bigint       not null references events (id) on delete cascade,
    extension    varchar(8)   not null,
    content_type varchar(40)  not null,
    byte_size    bigint       not null,
    url          varchar(500) not null,
    scrim        double precision not null,
    caption      varchar(120),
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

create index ix_event_assets_event on event_assets (event_id);
