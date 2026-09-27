-- gasnet 检漏数据批量导入（Excel 上传校验/去重/错误报告）增量迁移 2026-09-27
-- 适用于已按旧版 gasnet.sql 建库的部署：t_gas_imp_row 扩展原始值/错误留痕列，
-- t_gas_leak_check 增加导入批次号与经纬度。新库直接执行 gasnet.sql 即可，无需本脚本。

ALTER TABLE t_gas_imp_row
  ADD COLUMN item_code varchar(64) DEFAULT NULL COMMENT '检测设备/项代号(选填)' AFTER row_no,
  ADD COLUMN site_no varchar(64) DEFAULT NULL COMMENT '管段编号(原始值)' AFTER item_code,
  ADD COLUMN detect_time datetime DEFAULT NULL COMMENT '检测时间(解析成功值)' AFTER site_no,
  ADD COLUMN detect_time_raw varchar(64) DEFAULT NULL COMMENT '检测时间原始文本' AFTER detect_time,
  ADD COLUMN conc_raw varchar(64) DEFAULT NULL COMMENT '浓度原始文本' AFTER qty,
  ADD COLUMN lng decimal(10,6) DEFAULT NULL COMMENT '经度(解析成功值)' AFTER conc_raw,
  ADD COLUMN lng_raw varchar(64) DEFAULT NULL COMMENT '经度原始文本' AFTER lng,
  ADD COLUMN lat decimal(10,6) DEFAULT NULL COMMENT '纬度(解析成功值)' AFTER lng_raw,
  ADD COLUMN lat_raw varchar(64) DEFAULT NULL COMMENT '纬度原始文本' AFTER lat,
  ADD COLUMN err_msg varchar(500) DEFAULT NULL COMMENT '校验失败原因(多项以；分隔)' AFTER lat_raw,
  ADD COLUMN check_id bigint DEFAULT NULL COMMENT '入库后关联正式记录 t_gas_leak_check.id' AFTER err_msg,
  MODIFY COLUMN row_no int DEFAULT NULL COMMENT '原表行次(Excel行号，表头为第1行)',
  MODIFY COLUMN qty decimal(12,2) DEFAULT NULL COMMENT '本行浓度读数ppm(解析成功值)',
  MODIFY COLUMN status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回(校验失败) 3重复跳过',
  ADD KEY idx_gas_imp_row_batch (batch_no, status);

ALTER TABLE t_gas_leak_check
  ADD COLUMN batch_no varchar(64) DEFAULT NULL COMMENT '检漏数据导入批次号(手工录入为NULL)' AFTER recheck_remark,
  ADD COLUMN lng decimal(10,6) DEFAULT NULL COMMENT '检测点经度' AFTER batch_no,
  ADD COLUMN lat decimal(10,6) DEFAULT NULL COMMENT '检测点纬度' AFTER lng,
  ADD KEY idx_gas_leak_batch (batch_no);
