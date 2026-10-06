-- 기본 템플릿은 공통, 관리자가 등록한 템플릿은 소유자에게만 공개한다.
ALTER TABLE event_templates ADD COLUMN owner_admin_id BIGINT
    REFERENCES admins(id) ON DELETE RESTRICT;
CREATE INDEX ix_event_templates_owner ON event_templates(owner_admin_id, is_active);
