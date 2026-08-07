-- 为 exception_8d 表新增 d1_members 列，存储结构化团队成员列表（JSONB）
-- D1 流程改造：负责人自行组建团队并提交质量部门审核，结构化成员信息独立存储
ALTER TABLE qms.exception_8d
    ADD COLUMN IF NOT EXISTS d1_members JSONB;

COMMENT ON COLUMN qms.exception_8d.d1_members IS 'D1团队成员结构化列表：[{userId, realName, roleCode}]，JSONB格式';
