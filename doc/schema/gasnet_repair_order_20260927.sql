-- gasnet 抢修工单五段处置流转 + 不可篡改封存 增量迁移 2026-09-27
-- 适用于已按旧版 gasnet.sql 建库的部署：t_gas_repair_order 由"四段推进/回退"骨架
-- 重构为"待接单→已接单→抢修中→待复核→复核通过→已封卡"严格顺序状态机，
-- 新增处置履历表与超期预警列。新库直接执行 gasnet.sql 即可，无需本脚本。

-- 0) 迁移前数据规整（必须在下方触发器创建之前完成，否则 status=2 旧行将被封存触发器拦死）：
--    a. 历史空工单号补占位号，避免 NOT NULL + 唯一索引冲突；
--    b. 旧版 status=2("已封存")语义已废（旧骨架从未走通过完整流程），连同异常数据
--       一律重置为 0待接单，按新五段状态机重新流转；
--    c. 记事列加宽到 1000。
UPDATE t_gas_repair_order
SET biz_no = CONCAT('WX-LEGACY-', id)
WHERE biz_no IS NULL OR TRIM(biz_no) = '';

UPDATE t_gas_repair_order
SET stage = 0, status = 0
WHERE status IS NULL OR status NOT IN (0, 1) OR status = 2 OR stage IS NULL OR stage NOT IN (0, 1, 2, 3, 4, 5);

ALTER TABLE t_gas_repair_order
  MODIFY COLUMN content varchar(1000) DEFAULT NULL COMMENT '险情描述/工单记事';

-- 1) 抢修工单主表：补齐五段流转、分阶段操作人/时间/备注、办结时限与预警列
ALTER TABLE t_gas_repair_order
  MODIFY COLUMN biz_no varchar(64) NOT NULL COMMENT '抢修工单号',
  ADD COLUMN source int DEFAULT 0 COMMENT '工单来源 0巡检上报 1群众报修 2应急接警' AFTER biz_no,
  ADD COLUMN site_no varchar(64) DEFAULT NULL COMMENT '涉气管段/设施编号(可空)' AFTER source,
  ADD COLUMN site_name varchar(255) DEFAULT NULL COMMENT '泄漏/险情发生地点' AFTER site_no,
  ADD COLUMN leak_level int DEFAULT 1 COMMENT '紧急程度 0一般 1紧急 2特急' AFTER site_name,
  ADD COLUMN contact_person varchar(64) DEFAULT NULL COMMENT '报告/现场联系人' AFTER content,
  ADD COLUMN contact_phone varchar(32) DEFAULT NULL COMMENT '联系电话' AFTER contact_person,
  ADD COLUMN owner_id bigint DEFAULT NULL COMMENT '负责人用户id(接单前为指派负责人,接单后为接单巡检员)' AFTER contact_phone,
  ADD COLUMN owner_name varchar(64) DEFAULT NULL COMMENT '负责人姓名(冗余)' AFTER owner_id,
  ADD COLUMN accept_by varchar(64) DEFAULT NULL COMMENT '接单巡检员(登录名)' AFTER status,
  ADD COLUMN accept_time datetime DEFAULT NULL COMMENT '接单时间' AFTER accept_by,
  ADD COLUMN repair_start_by varchar(64) DEFAULT NULL COMMENT '开始抢修操作人' AFTER accept_time,
  ADD COLUMN repair_start_time datetime DEFAULT NULL COMMENT '开始抢修时间' AFTER repair_start_by,
  ADD COLUMN repair_result varchar(2000) DEFAULT NULL COMMENT '抢修结果(处置措施/更换部件/复测情况)' AFTER repair_start_time,
  ADD COLUMN submit_by varchar(64) DEFAULT NULL COMMENT '提交抢修结果操作人' AFTER repair_result,
  ADD COLUMN submit_time datetime DEFAULT NULL COMMENT '提交抢修结果时间' AFTER submit_by,
  ADD COLUMN review_by varchar(64) DEFAULT NULL COMMENT '现场复核操作人' AFTER submit_time,
  ADD COLUMN review_time datetime DEFAULT NULL COMMENT '现场复核时间' AFTER review_by,
  ADD COLUMN review_remark varchar(1000) DEFAULT NULL COMMENT '现场复核意见' AFTER review_time,
  ADD COLUMN seal_by varchar(64) DEFAULT NULL COMMENT '完工封卡操作人' AFTER review_remark,
  ADD COLUMN seal_time datetime DEFAULT NULL COMMENT '完工封卡时间' AFTER seal_by,
  ADD COLUMN due_at datetime DEFAULT NULL COMMENT '办结时限(超过即超期预警)' AFTER seal_time,
  ADD COLUMN warn_count int DEFAULT 0 COMMENT '已发超期预警次数' AFTER due_at,
  ADD COLUMN last_warn_time datetime DEFAULT NULL COMMENT '最近一次超期预警时间(同单24小时内不重复提醒)' AFTER warn_count,
  DROP COLUMN last_action,
  MODIFY COLUMN stage int NOT NULL DEFAULT 0 COMMENT '当前处置段 0待接单 1已接单 2抢修中 3待复核 4复核通过 5已封卡',
  MODIFY COLUMN status int NOT NULL DEFAULT 0 COMMENT '工单状态 0待接单(未起) 1在办 2已封卡(永久封存)',
  ADD UNIQUE KEY uk_gas_repair_biz_no (biz_no),
  ADD KEY idx_gas_repair_stage (stage, status, del_flag),
  ADD KEY idx_gas_repair_due (due_at);

-- 2) 处置履历表：每一处置段落一条（操作人/操作时间/备注/环节快照），只增不改不删
CREATE TABLE IF NOT EXISTS t_gas_repair_order_record (
  id bigint NOT NULL COMMENT '主键',
  order_id bigint NOT NULL COMMENT '抢修工单 t_gas_repair_order.id',
  biz_no varchar(64) DEFAULT NULL COMMENT '抢修工单号(冗余)',
  stage int DEFAULT NULL COMMENT '动作完成后所处处置段 0待接单 1已接单 2抢修中 3待复核 4复核通过 5已封卡',
  stage_name varchar(32) DEFAULT NULL COMMENT '处置段名快照(待接单/已接单/抢修中/待复核/复核通过/已封卡)',
  action int DEFAULT NULL COMMENT '过口动作 0创建 1接单 2开始抢修 3提交抢修结果 4现场复核 5完工封卡',
  operator varchar(64) DEFAULT NULL COMMENT '操作人(登录名)',
  remark varchar(1000) DEFAULT NULL COMMENT '操作备注/处置说明',
  create_time datetime DEFAULT NULL COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_gas_repair_record_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抢修工单处置履历';

-- 3) 不可篡改兜底：库级触发器
--    已封卡工单禁止 UPDATE/DELETE；履历表只增不改不删。
--    含 IF 复合语句，须用 mysql 客户端按 DELIMITER 分段执行；
--    JDBC/程序化执行时请逐触发器发送（语句体内含分号）。
DROP TRIGGER IF EXISTS trg_gas_repair_order_no_update_sealed;
DROP TRIGGER IF EXISTS trg_gas_repair_order_no_delete_sealed;
DROP TRIGGER IF EXISTS trg_gas_repair_record_no_update;
DROP TRIGGER IF EXISTS trg_gas_repair_record_no_delete;

DELIMITER //

CREATE TRIGGER trg_gas_repair_order_no_update_sealed
BEFORE UPDATE ON t_gas_repair_order
FOR EACH ROW
BEGIN
    IF OLD.status = 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '该抢修工单已完工封卡，永久封存，不得修改任何内容';
    END IF;
END//

CREATE TRIGGER trg_gas_repair_order_no_delete_sealed
BEFORE DELETE ON t_gas_repair_order
FOR EACH ROW
BEGIN
    IF OLD.status = 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '该抢修工单已完工封卡，永久封存，不得删除';
    END IF;
END//

CREATE TRIGGER trg_gas_repair_record_no_update
BEFORE UPDATE ON t_gas_repair_order_record
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '抢修处置履历只增不改，禁止更新';
END//

CREATE TRIGGER trg_gas_repair_record_no_delete
BEFORE DELETE ON t_gas_repair_order_record
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '抢修处置履历只增不删，禁止删除';
END//

DELIMITER ;
