-- 首件检验主记录新增 std_version 列：建单时固化所引用检验标准的版本号，
-- 用于版本隔离（刷新标准/标准变更对比按记录自身版本定位，不受标准库后续新建版本影响）。
-- 仅加列，无删改，符合迁移脚本铁律。
ALTER TABLE qms.fai_inspection_record ADD COLUMN std_version INTEGER;

COMMENT ON COLUMN qms.fai_inspection_record.std_version IS '建单时固化的检验标准版本号；为空表示历史数据，回退取最新激活版本';
