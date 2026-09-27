-- gasnet 城市燃气泄漏检测与管道安全巡检管理 -- schema (hai-005)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_006

CREATE TABLE IF NOT EXISTS t_gas_conc_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '泄漏浓度判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判线名',
  version int DEFAULT '1' COMMENT '规则版本号(同一rule_code下自增)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '轻度档浓度上限(ppm)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中度档浓度上限(ppm)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '重度档浓度上限(ppm)',
  eff_start datetime DEFAULT NULL COMMENT '启用之日(含)',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含,NULL为长期有效)',
  priority int DEFAULT NULL COMMENT '让线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='泄漏浓度分档判定线';

CREATE TABLE IF NOT EXISTS t_gas_imp_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '检漏数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次(Excel行号，表头为第1行)',
  item_code varchar(64) DEFAULT NULL COMMENT '检测设备/项代号(选填)',
  site_no varchar(64) DEFAULT NULL COMMENT '管段编号(原始值)',
  detect_time datetime DEFAULT NULL COMMENT '检测时间(解析成功值)',
  detect_time_raw varchar(64) DEFAULT NULL COMMENT '检测时间原始文本',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行浓度读数ppm(解析成功值)',
  conc_raw varchar(64) DEFAULT NULL COMMENT '浓度原始文本',
  lng decimal(10,6) DEFAULT NULL COMMENT '经度(解析成功值)',
  lng_raw varchar(64) DEFAULT NULL COMMENT '经度原始文本',
  lat decimal(10,6) DEFAULT NULL COMMENT '纬度(解析成功值)',
  lat_raw varchar(64) DEFAULT NULL COMMENT '纬度原始文本',
  err_msg varchar(500) DEFAULT NULL COMMENT '校验失败原因(多项以；分隔)',
  check_id bigint DEFAULT NULL COMMENT '入库后关联正式记录 t_gas_leak_check.id',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回(校验失败) 3重复跳过',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_gas_imp_row_batch (batch_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检漏数据导入核对行';

CREATE TABLE IF NOT EXISTS t_gas_leak_check (
  id bigint NOT NULL COMMENT '主键',
  check_no varchar(64) DEFAULT NULL COMMENT '检测评定记录号',
  detect_time datetime DEFAULT NULL COMMENT '现场检测时间(以此定位在用规则版本)',
  site_no varchar(64) DEFAULT NULL COMMENT '检测点代号',
  site_name varchar(128) DEFAULT NULL COMMENT '检测点名称',
  conc_value decimal(12,2) DEFAULT NULL COMMENT '原始浓度读数(ppm)',
  grade_level int DEFAULT NULL COMMENT '评定等级 1轻微 2中度 3严重 4极严重 0无法判定',
  rule_id bigint DEFAULT NULL COMMENT '评定时适用规则版本主键(快照)',
  rule_code varchar(64) DEFAULT NULL COMMENT '适用规则代号(快照)',
  rule_version int DEFAULT NULL COMMENT '适用规则版本号(快照)',
  recheck_status int DEFAULT '0' COMMENT '复核情形 0未复核 1已重算 2重算后等级变更',
  recheck_time datetime DEFAULT NULL COMMENT '最近复核时间',
  recheck_by varchar(64) DEFAULT NULL COMMENT '复核人',
  recheck_grade_level int DEFAULT NULL COMMENT '复核后等级',
  recheck_rule_id bigint DEFAULT NULL COMMENT '复核时适用规则版本主键',
  recheck_rule_version int DEFAULT NULL COMMENT '复核时适用规则版本号',
  recheck_remark varchar(500) DEFAULT NULL COMMENT '复核说明',
  batch_no varchar(64) DEFAULT NULL COMMENT '检漏数据导入批次号(手工录入为NULL)',
  lng decimal(10,6) DEFAULT NULL COMMENT '检测点经度',
  lat decimal(10,6) DEFAULT NULL COMMENT '检测点纬度',
  status int DEFAULT '0' COMMENT '记录情形 0有效 1作废',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gas_leak_check_no (check_no),
  KEY idx_gas_leak_detect_time (detect_time),
  KEY idx_gas_leak_rule (rule_id),
  KEY idx_gas_leak_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='泄漏浓度检测评定记录';

CREATE TABLE IF NOT EXISTS t_gas_pipe (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '管段/调压站档案代号',
  site_name varchar(128) DEFAULT NULL COMMENT '档案名称',
  site_type varchar(32) DEFAULT NULL COMMENT '档案类别(管道/调压站)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档管线长度一档上限(km)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(km)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(km)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已退役',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管段/调压站底档';

CREATE TABLE IF NOT EXISTS t_gas_pipe_arch (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '管道/调压站档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡管线长度(km)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增管线长度(km)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '管线长度折到的巡检强度层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管道/调压站档案卡';

CREATE TABLE IF NOT EXISTS t_gas_plan_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '巡检派单事项号',
  site_id bigint DEFAULT NULL COMMENT '关联管段底档 t_gas_pipe.id(NULL为未关联)',
  site_no varchar(64) DEFAULT NULL COMMENT '关联管段底档代号(冗余)',
  due_at datetime DEFAULT NULL COMMENT '该动手那一天的最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前派单的自然日数',
  content varchar(255) DEFAULT NULL COMMENT '派单事由与受派班组记要',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2派不出去 3已取消(管段停用)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_gas_plan_task_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='巡检计划到期任务派单';

CREATE TABLE IF NOT EXISTS t_gas_repair_order (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '抢修工单号',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（接单/抢修/复核/封卡）',
  status int DEFAULT NULL COMMENT '工单落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '工单记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抢修工单';

CREATE TABLE IF NOT EXISTS t_gas_stop_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '停用申请签核单号',
  site_id bigint DEFAULT NULL COMMENT '所属管段底档 t_gas_pipe.id',
  site_no varchar(64) DEFAULT NULL COMMENT '所属管段底档代号(快照)',
  apply_reason varchar(500) DEFAULT NULL COMMENT '停用申请理由(重大隐患/达到设计寿命等)',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..3（0运维提/1安全核/2工程评/3管理层批）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本口应落印数',
  sign_count int DEFAULT NULL COMMENT '本口已落印数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回 3已否决',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_gas_stop_bill_site (site_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='燃气管段停用申请签核单';

CREATE TABLE IF NOT EXISTS t_gas_stop_bill_record (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint DEFAULT NULL COMMENT '签核单 t_gas_stop_bill.id',
  bill_no varchar(64) DEFAULT NULL COMMENT '签核单号(冗余)',
  node_no int DEFAULT NULL COMMENT '动作发生时所在道 0..3（0运维提/1安全核/2工程评/3管理层批）',
  node_name varchar(32) DEFAULT NULL COMMENT '环节名快照(运维申请/安全审核/工程评估/管理层批准)',
  action int DEFAULT NULL COMMENT '过口动作 0提交 1通过 2退回补充 3否决',
  approver varchar(64) DEFAULT NULL COMMENT '审批人(操作人登录名)',
  opinion varchar(500) DEFAULT NULL COMMENT '审批意见',
  create_time datetime DEFAULT NULL COMMENT '审批时间',
  PRIMARY KEY (id),
  KEY idx_gas_stop_record_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='燃气管段停用申请审批履历';

CREATE TABLE IF NOT EXISTS t_gas_task_dispatch (
  id bigint NOT NULL COMMENT '主键',
  dispatch_no varchar(64) DEFAULT NULL COMMENT '派发单号',
  task_id bigint DEFAULT NULL COMMENT '关联到期任务 t_gas_plan_task.id',
  item_no varchar(64) DEFAULT NULL COMMENT '巡检派单事项号(冗余)',
  target_type int DEFAULT NULL COMMENT '接收对象 0班组 1巡检员',
  target_id varchar(64) DEFAULT NULL COMMENT '接收对象主键(部门/用户)',
  target_name varchar(128) DEFAULT NULL COMMENT '接收对象名称(冗余)',
  dispatch_mode int DEFAULT NULL COMMENT '派发方式 0自动扫描 1手动指派',
  urgent int DEFAULT NULL COMMENT '紧急程度 0普通 1紧急(超期3天及以上)',
  overdue_days int DEFAULT NULL COMMENT '派发时已超期自然日数(未超期为0)',
  dispatch_time datetime DEFAULT NULL COMMENT '派发时间',
  dispatch_by varchar(64) DEFAULT NULL COMMENT '派发人(自动派发为system)',
  exec_status int DEFAULT NULL COMMENT '执行情况 0待执行 1执行中 2已完成',
  finish_time datetime DEFAULT NULL COMMENT '执行完成时间',
  status int DEFAULT NULL COMMENT '记录情形 0有效 1已撤销',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_gas_dispatch_task (task_id),
  KEY idx_gas_dispatch_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='巡检到期任务派发记录';

-- 初始档案数据：id=0 在用、id=1 已退役
-- 管线长度上限 30/80/200 km：等于上限算高一档。
INSERT IGNORE INTO t_gas_pipe (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'GP00', '青坪市城区中压主干管段（在用）', '管道', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'GP01', '青坪市城东调压站（已退役）', '调压站', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 1, 0, 'seed', NOW());

