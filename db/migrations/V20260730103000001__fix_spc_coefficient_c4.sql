-- 修复 spc_coefficient 表 c4 系数缺失问题。
UPDATE qms.spc_coefficient SET c4 = 0.7979 WHERE n = 2;
UPDATE qms.spc_coefficient SET c4 = 0.8862 WHERE n = 3;
UPDATE qms.spc_coefficient SET c4 = 0.9213 WHERE n = 4;
UPDATE qms.spc_coefficient SET c4 = 0.9400 WHERE n = 5;
UPDATE qms.spc_coefficient SET c4 = 0.9515 WHERE n = 6;
UPDATE qms.spc_coefficient SET c4 = 0.9594 WHERE n = 7;
UPDATE qms.spc_coefficient SET c4 = 0.9650 WHERE n = 8;
UPDATE qms.spc_coefficient SET c4 = 0.9693 WHERE n = 9;
UPDATE qms.spc_coefficient SET c4 = 0.9727 WHERE n = 10;
UPDATE qms.spc_coefficient SET c4 = 0.9754 WHERE n = 11;
UPDATE qms.spc_coefficient SET c4 = 0.9776 WHERE n = 12;
