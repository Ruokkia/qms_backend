-- 修复 fai_inspection_standard 表中 last_reviewed_at / last_used_at 列类型
-- 问题：建表时其他时间列均为 timestamp without time zone（无时区，对应 Java LocalDateTime），
--       但 V20260804220000003 误用 TIMESTAMPTZ（带时区），导致 JDBC 无法将 TIMESTAMPTZ 映射为 LocalDateTime，
--       查询该表时抛 BadSqlGrammarException，检验标准维护页面无数据。
-- 修复：将这两列改为与全表一致的 TIMESTAMP（无时区）。

ALTER TABLE qms.fai_inspection_standard
    ALTER COLUMN last_reviewed_at TYPE TIMESTAMP,
    ALTER COLUMN last_used_at TYPE TIMESTAMP;
