-- ============================================================================
-- 补执行 V20260807200000001 中被跳过的 DDL（因更高版本已先应用）
--
-- 变更内容：
--   1. 新增 extra_data JSONB 列（存储跳转参数和摘要信息）
--   2. 新增 expire_at TIMESTAMP 列（支持 TTL 过期通知）
--   3. 新增复合索引 idx_notification_user_unread_created
-- ============================================================================

-- 1. 新增 extra_data JSONB 列（可空）
ALTER TABLE qms.notification
    ADD COLUMN IF NOT EXISTS extra_data jsonb;

COMMENT ON COLUMN qms.notification.extra_data IS '扩展数据（JSONB）：存储跳转参数、摘要信息';

-- 2. 新增 expire_at TIMESTAMP 列（可空，支持 TTL 过期通知）
ALTER TABLE qms.notification
    ADD COLUMN IF NOT EXISTS expire_at timestamp without time zone;

COMMENT ON COLUMN qms.notification.expire_at IS '通知过期时间（NULL 表示永不过期）';

-- 3. 新增复合索引：优化按用户+未读+时间倒序的分页查询
CREATE INDEX IF NOT EXISTS idx_notification_user_unread_created
    ON qms.notification (user_id, is_read, created_at DESC)
    WHERE (is_deleted = 0);
