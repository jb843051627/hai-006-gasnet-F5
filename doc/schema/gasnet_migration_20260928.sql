-- gasnet 抢修工单处置流程（五阶段顺序流转 + 履历留痕 + 完工封卡 + 超期预警）增量迁移 2026-09-28
-- 适用于已按旧版 gasnet.sql 建库的部署：t_gas_repair_order 由“0..3 推进/回退”骨架
-- 扩展为“接单 → 开始抢修 → 提交抢修结果 → 现场复核 → 完工封卡”五段状态机，
-- 新增处置履历表。新库直接执行 gasnet.sql 即可，无需本脚本。

-- 1) 抢修工单主表扩展：阶段改 0..5，补齐接单人/负责人/时限/各阶段时间/预警字段
ALTER TABLE t_gas_repair_order
  MODIFY COLUMN stage int DEFAULT 0 COMMENT '当前处置段 0待接单 1已接单 2抢修中 3待复核 4待封卡 5已封存',
  MODIFY COLUMN status int DEFAULT 0 COMMENT '工单落定 0待接单 1在办 2已封存',
  MODIFY COLUMN content varchar(500) DEFAULT NULL COMMENT '险情记事(泄漏位置/险情描述等)',
  ADD COLUMN owner_id bigint DEFAULT NULL COMMENT '负责人 t_sys_user.id(超期预警接收人)' AFTER status,
  ADD COLUMN owner varchar(64) DEFAULT NULL COMMENT '负责人登录名(快照)' AFTER owner_id,
  ADD COLUMN owner_name varchar(64) DEFAULT NULL COMMENT '负责人姓名(快照)' AFTER owner,
  ADD COLUMN assignee_id bigint DEFAULT NULL COMMENT '接单巡检员 t_sys_user.id' AFTER owner_name,
  ADD COLUMN assignee varchar(64) DEFAULT NULL COMMENT '接单人登录名(快照)' AFTER assignee_id,
  ADD COLUMN assignee_name varchar(64) DEFAULT NULL COMMENT '接单人姓名(快照)' AFTER assignee,
  ADD COLUMN due_at datetime DEFAULT NULL COMMENT '处置时限(超期未封卡即预警)' AFTER assignee_name,
  ADD COLUMN repair_result varchar(1000) DEFAULT NULL COMMENT '抢修结果(处置措施/换件/恢复供气等)' AFTER due_at,
  ADD COLUMN claim_time datetime DEFAULT NULL COMMENT '接单时间' AFTER repair_result,
  ADD COLUMN start_time datetime DEFAULT NULL COMMENT '开始抢修时间' AFTER claim_time,
  ADD COLUMN submit_time datetime DEFAULT NULL COMMENT '提交抢修结果时间' AFTER start_time,
  ADD COLUMN review_time datetime DEFAULT NULL COMMENT '现场复核时间' AFTER submit_time,
  ADD COLUMN seal_time datetime DEFAULT NULL COMMENT '完工封卡时间' AFTER review_time,
  ADD COLUMN warn_count int DEFAULT 0 COMMENT '超期预警已发次数' AFTER seal_time,
  ADD COLUMN last_warn_time datetime DEFAULT NULL COMMENT '最近一次超期预警时间(24小时内不重复提醒)' AFTER warn_count,
  ADD KEY idx_gas_repair_stage (status, stage),
  ADD KEY idx_gas_repair_due (due_at);

-- 旧骨架数据归一：
-- 旧 stage 0接单/1抢修/2复核/3封卡 → 新 1已接单/2抢修中/3待复核/4待封卡（在办单整体后移一档）
UPDATE t_gas_repair_order SET stage = stage + 1 WHERE status = 1 AND stage IS NOT NULL AND stage BETWEEN 0 AND 3;
-- 已封存(status=2)统一落到终态 stage=5
UPDATE t_gas_repair_order SET stage = 5 WHERE status = 2;
-- 未起(status=0)或历史脏数据归一为待接单
UPDATE t_gas_repair_order SET status = 0, stage = 0 WHERE status IS NULL OR (status = 0 AND stage IS NULL);

-- 2) 抢修工单处置履历表：每个过口动作一条，只增不改
CREATE TABLE IF NOT EXISTS t_gas_repair_order_record (
  id bigint NOT NULL COMMENT '主键',
  order_id bigint DEFAULT NULL COMMENT '抢修工单 t_gas_repair_order.id',
  biz_no varchar(64) DEFAULT NULL COMMENT '抢修工单号(冗余)',
  stage int DEFAULT NULL COMMENT '动作发生时所处段 0接单 1开始抢修 2提交抢修结果 3现场复核 4完工封卡',
  stage_name varchar(32) DEFAULT NULL COMMENT '环节名快照(接单/开始抢修/提交抢修结果/现场复核/完工封卡)',
  action int DEFAULT NULL COMMENT '过口动作 0接单 1开始抢修 2提交抢修结果 3现场复核 4完工封卡',
  operator varchar(64) DEFAULT NULL COMMENT '操作人登录名',
  operator_name varchar(64) DEFAULT NULL COMMENT '操作人姓名(快照)',
  opinion varchar(500) DEFAULT NULL COMMENT '操作备注',
  create_time datetime DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_gas_repair_record_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抢修工单处置履历(只增不改)';

-- 3) 超期未封卡工单预警定时任务：每小时扫描一次（status 0正常 1暂停）
INSERT IGNORE INTO t_sys_quartz_job (id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (9003, '抢修工单超期预警', 'GASNET', 'gasRepairOrderWarnTask.scanOverdue()', '0 0 * * * ?', '2', '1', 0, 'seed', NOW(), '每小时扫描处置时限已过仍未完工封卡的抢修工单，站内信提醒负责人/接单人督促处置，同一工单24小时内不重复提醒');
