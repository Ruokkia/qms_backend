-- ============================================================
-- sys_auth_seed.sql
-- 系统认证模块种子数据（角色 + 双分公司测试用户）
-- 版本：V1.0
-- 日期：2026-07-17
-- 数据库：PostgreSQL 14+
-- ============================================================
-- 密码说明：
--   所有测试账号密码均为 "123456"，以下为 BCrypt(rounds=10) 哈希。
--   通过 python bcrypt：bcrypt.hashpw(b'123456', bcrypt.gensalt(rounds=10))
--   后端 Spring Security BCryptPasswordEncoder 可直接校验。
--   ⚠️ 上线前必须重置所有密码为强密码！
-- ============================================================

-- ============================================================
-- 1. 角色种子数据（6 角色，全局共享）
-- ============================================================
INSERT INTO qms.sys_role (role_code, role_name, description, status, plant_code, plant_name, created_by, created_at, updated_at) VALUES
('R01', '操作工',          '产线操作人员，主要负责数据录入与查看',           1, '*', '全局', 'SYSTEM', now(), now()),
('R02', '检验员',          '质量检验人员，负责来料/过程/成品检验',           1, '*', '全局', 'SYSTEM', now(), now()),
('R03', '班组长',          '生产班组长，负责班组管理与不良处理',             1, '*', '全局', 'SYSTEM', now(), now()),
('R04', '质量工程师',      '质量工程技术人员，负责质量分析与改进',           1, '*', '全局', 'SYSTEM', now(), now()),
('R05', 'SQE供应商质量',   '供应商质量管理工程师，负责供应商审核与质量提升', 1, '*', '全局', 'SYSTEM', now(), now()),
('R06', '质量经理',        '质量管理部门经理，具备全部权限及分公司切换能力', 1, '*', '全局', 'SYSTEM', now(), now());

-- ============================================================
-- 2. 测试用户种子数据（深圳 SZ x 6 + 梅州 MZ x 6 = 12 个）
-- ============================================================
-- BCrypt hash for '123456' (rounds=10), 3 个不同盐值轮换使用
INSERT INTO qms.sys_user (account, password_hash, real_name, role_code, plant_code, plant_name, status, created_by, created_at, updated_at) VALUES
-- 深圳分公司（SZ）
('sz_op01',   '$2b$10$XmwOkbcMKBN.BTnIl7FMKe.m7Q0dLH5/MevC7mIClbW8P7Q1A1qTO', '张三', 'R01', 'SZ', '深圳', 1, 'SYSTEM', now(), now()),
('sz_insp01', '$2b$10$lCAXTEF07yPA0c7ky4VzCuZ7Lb4q457mxm4gJNXvBaaDZywQRw3N.', '李四', 'R02', 'SZ', '深圳', 1, 'SYSTEM', now(), now()),
('sz_lead01', '$2b$10$LfB6nByVg4.YNzKlQXTsNuf/wTzba.gVbL89uzt2CiRwodflq0zqG', '王五', 'R03', 'SZ', '深圳', 1, 'SYSTEM', now(), now()),
('sz_qe01',   '$2b$10$XmwOkbcMKBN.BTnIl7FMKe.m7Q0dLH5/MevC7mIClbW8P7Q1A1qTO', '赵六', 'R04', 'SZ', '深圳', 1, 'SYSTEM', now(), now()),
('sz_sqe01',  '$2b$10$lCAXTEF07yPA0c7ky4VzCuZ7Lb4q457mxm4gJNXvBaaDZywQRw3N.', '钱七', 'R05', 'SZ', '深圳', 1, 'SYSTEM', now(), now()),
('sz_mgr01',  '$2b$10$LfB6nByVg4.YNzKlQXTsNuf/wTzba.gVbL89uzt2CiRwodflq0zqG', '孙八', 'R06', 'SZ', '深圳', 1, 'SYSTEM', now(), now()),
-- 梅州分公司（MZ）
('mz_op01',   '$2b$10$XmwOkbcMKBN.BTnIl7FMKe.m7Q0dLH5/MevC7mIClbW8P7Q1A1qTO', '陈一', 'R01', 'MZ', '梅州', 1, 'SYSTEM', now(), now()),
('mz_insp01', '$2b$10$lCAXTEF07yPA0c7ky4VzCuZ7Lb4q457mxm4gJNXvBaaDZywQRw3N.', '周二', 'R02', 'MZ', '梅州', 1, 'SYSTEM', now(), now()),
('mz_lead01', '$2b$10$LfB6nByVg4.YNzKlQXTsNuf/wTzba.gVbL89uzt2CiRwodflq0zqG', '吴三', 'R03', 'MZ', '梅州', 1, 'SYSTEM', now(), now()),
('mz_qe01',   '$2b$10$XmwOkbcMKBN.BTnIl7FMKe.m7Q0dLH5/MevC7mIClbW8P7Q1A1qTO', '郑四', 'R04', 'MZ', '梅州', 1, 'SYSTEM', now(), now()),
('mz_sqe01',  '$2b$10$lCAXTEF07yPA0c7ky4VzCuZ7Lb4q457mxm4gJNXvBaaDZywQRw3N.', '冯五', 'R05', 'MZ', '梅州', 1, 'SYSTEM', now(), now()),
('mz_mgr01',  '$2b$10$LfB6nByVg4.YNzKlQXTsNuf/wTzba.gVbL89uzt2CiRwodflq0zqG', '褚六', 'R06', 'MZ', '梅州', 1, 'SYSTEM', now(), now());
