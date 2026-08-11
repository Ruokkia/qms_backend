-- ============================================================================
-- 2026-08-07: BOTH / 8D 模式 CAPA-8D 交错流程
-- 为 exception_order 表添加 capa_phase 字段，记录 CAPA 治理阶段。
--
-- 阶段编排：
--   INITIATE             → CAPA 立项完成，8D D1-D4 进行中
--   ROOT_CAUSE_APPROVED  → 根因审批通过，8D D5 准入
--   MEASURES_APPROVED    → 措施审批通过，8D D6-D8 准入
--   CLOSED               → 已闭环（异常单 close 时设置）
--
-- 说明：该字段为 Flyway 迁移管理，便于部署时自动建字段。
-- ============================================================================

ALTER TABLE exception_order ADD COLUMN IF NOT EXISTS capa_phase VARCHAR(50);

COMMENT ON COLUMN exception_order.capa_phase IS 'CAPA 治理阶段（含 8D 流程）: INITIATE/ROOT_CAUSE_APPROVED/MEASURES_APPROVED/CLOSED';
