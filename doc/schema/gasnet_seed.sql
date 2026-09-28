-- 初始档案数据：id=0 在用、id=1 已退役
-- 管线长度上限 30/80/200 km：等于上限算高一档。
INSERT IGNORE INTO t_gas_pipe (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'GP00', '青坪市城区中压主干管段（在用）', '管道', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'GP01', '青坪市城东调压站（已退役）', '调压站', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 1, 0, 'seed', NOW());

-- 巡检班组（挂在总部节点 id=1 之下，status=1 启用）
INSERT IGNORE INTO t_sys_department (id, parent_id, dept_name, leader, phone, email, status, order_num, create_by, create_time)
VALUES (101, 1, '巡检一班', '班组长一', '13000000001', 'team1@gas.net', 1, 11, 'seed', NOW()),
       (102, 1, '巡检二班', '班组长二', '13000000002', 'team2@gas.net', 1, 12, 'seed', NOW()),
       (103, 1, '巡检三班', '班组长三', '13000000003', 'team3@gas.net', 1, 13, 'seed', NOW());

-- 到期任务派单演示数据：覆盖 超期5天/3天/1天、今日到期、窗口内未到期、窗口外、已派 七种情形
-- amount=提前派单自然日数；status 0待派 1已派 2派不出去 3已取消(管段停用)
-- site_id/site_no 关联管段底档：已停用管段(status=1)的任务不再被扫描派单；未关联(NULL)的按原样参与
INSERT IGNORE INTO t_gas_plan_task (id, item_no, site_id, site_no, due_at, amount, content, status, del_flag, create_by, create_time)
VALUES (1, 'PT-YEAR-0001', 0, 'GP00', DATE_SUB(NOW(), INTERVAL 5 DAY), 3, '年度检测：青坪市城区中压主干管段泄漏浓度普测', 0, 0, 'seed', NOW()),
       (2, 'PT-MON-0002', NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), 3, '月度巡检：城东区东湖街道调压柜运行状态检查', 0, 0, 'seed', NOW()),
       (3, 'PT-YEAR-0003', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), 3, '年度检测：城西片区中压支线管段防腐层检测', 0, 0, 'seed', NOW()),
       (4, 'PT-MON-0004', NULL, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 23:59:59'), 3, '月度巡检：城南片区庭院管网入户安全检查', 0, 0, 'seed', NOW()),
       (5, 'PT-SEAS-0005', NULL, NULL, DATE_ADD(NOW(), INTERVAL 2 DAY), 3, '季度检测：城北调压站压力容器定期校验', 0, 0, 'seed', NOW()),
       (6, 'PT-YEAR-0006', NULL, NULL, DATE_ADD(NOW(), INTERVAL 10 DAY), 3, '年度检测：开发区工业用户专线管段巡检', 0, 0, 'seed', NOW()),
       (7, 'PT-MON-0007', NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), 3, '月度巡检：老城区铸铁管段重点排查（已派）', 1, 0, 'seed', NOW());

-- 周期扫描定时任务：每日 01:00 扫描到期任务并自动派单（status 0正常 1暂停）
INSERT IGNORE INTO t_sys_quartz_job (id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (9001, '到期任务扫描派单', 'GASNET', 'gasPlanTaskScanTask.scanDueTasks()', '0 0 1 * * ?', '2', '1', 0, 'seed', NOW(), '每日扫描应派未派的巡检任务，按班组负载均衡自动派单，超期3天以上优先');

-- 浓度分档规则版本化演示：同一判线 CITY-MID 两个版本，2026-01-01 00:00:00 交棒
-- V1 阈值 20/50/100 ppm（已让位）；V2 收紧为 10/30/80 ppm（现行、长期有效）
-- 区间左闭右开：2025-12-31 23:59:59 的检测仍按 V1，2026-01-01 00:00:00 起按 V2
INSERT IGNORE INTO t_gas_conc_rule (id, rule_code, rule_name, version, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES (8001, 'CITY-MID', '城区中压管网浓度判线', 1, 20.00, 50.00, 100.00, '2025-01-01 00:00:00', '2026-01-01 00:00:00', 10, 1, 0, 'seed', NOW(), '初版判线'),
       (8002, 'CITY-MID', '城区中压管网浓度判线', 2, 10.00, 30.00, 80.00, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '依据新标准收紧阈值（等于上限取高一档）');

-- 检测评定记录演示（按各自检测时刻的在用版本判档，版本快照留痕）：
-- 8101 检测于交棒前，浓度15ppm：V1 下落轻微(1)（若误用 V2 会成中度(2)，历史评定不随后续改标漂移）
-- 8102 检测于交棒后，浓度15ppm：V2 下中度(2)
INSERT IGNORE INTO t_gas_leak_check (id, check_no, detect_time, site_no, site_name, conc_value, grade_level, rule_id, rule_code, rule_version, recheck_status, status, del_flag, create_by, create_time)
VALUES (8101, 'LC20251215100000-8101', '2025-12-15 10:00:00', 'GP00-V01', '青坪市城区中压主干管段东湖路口检测点', 15.00, 1, 8001, 'CITY-MID', 1, 0, 0, 0, 'seed', NOW()),
       (8102, 'LC20260920103000-8102', '2026-09-20 10:30:00', 'GP00-V01', '青坪市城区中压主干管段东湖路口检测点', 15.00, 2, 8002, 'CITY-MID', 2, 0, 0, 0, 'seed', NOW());

-- 规则交棒排期翻转定时任务：每分钟扫描 eff_end 已到但状态仍为现行的版本并翻转（status 0正常 1暂停）
INSERT IGNORE INTO t_sys_quartz_job (id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (9002, '浓度规则交棒翻转', 'GASNET', 'gasConcRuleRolloverTask.rollover()', '0 * * * * ?', '2', '1', 0, 'seed', NOW(), '每分钟扫描到点交棒的规则版本，翻转台账状态位（评定在否在用以时间窗为准）');

-- 管段停用申请演示：GP00 主干管段已达设计寿命且有重大隐患，运维已提交，现停在 1安全核 待审
-- node_no 0运维提/1安全核/2工程评/3管理层批；status 0在核 1已核讫 2已打回 3已否决
INSERT IGNORE INTO t_gas_stop_bill (id, bill_no, site_id, site_no, apply_reason, node_no, sign_mode, need_count, sign_count, status, del_flag, create_by, create_time)
VALUES (7001, 'SB20260926000001', 0, 'GP00', '管段服役已满设计寿命，近期泄漏普测浓度持续偏高，存在重大安全隐患，申请停用。', 1, 0, 1, 0, 0, 0, 'seed', NOW());

-- 停用申请审批履历演示：7001 的运维提交留痕（action 0提交 1通过 2退回补充 3否决）
INSERT IGNORE INTO t_gas_stop_bill_record (id, bill_id, bill_no, node_no, node_name, action, approver, opinion, create_time)
VALUES (7001, 7001, 'SB20260926000001', 0, '运维申请', 0, 'seed', '管段服役已满设计寿命，近期泄漏普测浓度持续偏高，存在重大安全隐患，申请停用。', NOW());

-- 抢修工单演示：覆盖 超期待接单 / 抢修中临期 / 全流程已封卡 三种情形
-- stage 0待接单 1已接单 2抢修中 3待复核 4复核通过 5已封卡；status 0待接单 1在办 2已封卡
-- 负责人均为 admin 用户(id=1)；6001 办结时限已过，下轮预警扫描即提醒；
-- 6003 已封卡，各阶段操作人/时间直接随 INSERT 落库（封卡后触发器禁止 UPDATE，不能再回填）。
INSERT IGNORE INTO t_gas_repair_order
(id, biz_no, source, site_no, site_name, leak_level, content, contact_person, contact_phone,
 owner_id, owner_name, stage, status,
 accept_by, accept_time, repair_start_by, repair_start_time, repair_result,
 submit_by, submit_time, review_by, review_time, review_remark, seal_by, seal_time,
 due_at, warn_count, del_flag, create_by, create_time)
VALUES
(6001, 'WX20260925090001-0001', 1, NULL, '城东区东湖街道东湖小区3号楼底商厨房', 2,
 '群众报修闻到明显燃气味，楼下燃气表箱处有嘶嘶漏气声，请求立即抢修。', '王女士', '13900000001',
 1, '管理员', 0, 0,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 0, 'seed', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6002, 'WX20260926103002-0002', 0, 'GP00-V07', '青坪市城区中压主干管段东湖路口阀井', 1,
 '巡检发现阀井法兰微漏，已接单并开挖，正在更换密封垫片。', '巡检一班', '13900000002',
 1, '管理员', 2, 1,
 'admin', DATE_SUB(NOW(), INTERVAL 23 HOUR), 'admin', DATE_SUB(NOW(), INTERVAL 20 HOUR), NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 DATE_ADD(NOW(), INTERVAL 3 HOUR), 0, 0, 'seed', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(6003, 'WX20260920083003-0003', 2, NULL, '城南工业大道18号调压站', 1,
 '应急接警：调压器出口压力异常，已完成泄放、换件、复测与现场复核，演示全流程封卡。', '值班长', '13900000003',
 1, '管理员', 5, 2,
 'admin', DATE_SUB(NOW(), INTERVAL 7 DAY), 'admin', DATE_SUB(NOW(), INTERVAL 7 DAY),
 '已更换主膜片与密封件，恢复供气后保压30分钟无压降，检漏仪复测浓度0ppm。',
 'admin', DATE_SUB(NOW(), INTERVAL 6 DAY), 'admin', DATE_SUB(NOW(), INTERVAL 6 DAY),
 '复核通过：现场压力、浓度复测合格，设备运行正常。', 'admin', DATE_SUB(NOW(), INTERVAL 6 DAY),
 DATE_ADD(NOW(), INTERVAL 1 DAY), 0, 0, 'seed', DATE_SUB(NOW(), INTERVAL 7 DAY));

-- 6001 创建履历
INSERT IGNORE INTO t_gas_repair_order_record (id, order_id, biz_no, stage, stage_name, action, operator, remark, create_time)
VALUES (6001, 6001, 'WX20260925090001-0001', 0, '待接单', 0, 'seed',
 '群众报修闻到明显燃气味，楼下燃气表箱处有嘶嘶漏气声，请求立即抢修。', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 6002 创建/接单/开工履历
INSERT IGNORE INTO t_gas_repair_order_record (id, order_id, biz_no, stage, stage_name, action, operator, remark, create_time)
VALUES
(6010, 6002, 'WX20260926103002-0002', 0, '待接单', 0, 'seed', '巡检发现阀井法兰微漏。', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(6011, 6002, 'WX20260926103002-0002', 1, '已接单', 1, 'admin', '巡检一班领取工单。', DATE_SUB(NOW(), INTERVAL 23 HOUR)),
(6012, 6002, 'WX20260926103002-0002', 2, '抢修中', 2, 'admin', '已开挖作业面，准备更换密封垫片。', DATE_SUB(NOW(), INTERVAL 20 HOUR));

-- 6003 全流程履历：创建→接单→开工→提交结果→复核通过→完工封卡
INSERT IGNORE INTO t_gas_repair_order_record (id, order_id, biz_no, stage, stage_name, action, operator, remark, create_time)
VALUES
(6020, 6003, 'WX20260920083003-0003', 0, '待接单', 0, 'seed', '应急接警：调压器出口压力异常。', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(6021, 6003, 'WX20260920083003-0003', 1, '已接单', 1, 'admin', '应急班组接单赶赴现场。', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(6022, 6003, 'WX20260920083003-0003', 2, '抢修中', 2, 'admin', '切断上游阀门并放空，开始更换调压器主膜片。', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(6023, 6003, 'WX20260920083003-0003', 3, '待复核', 3, 'admin',
 '已更换主膜片与密封件，恢复供气后保压30分钟无压降，检漏仪复测浓度0ppm。', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(6024, 6003, 'WX20260920083003-0003', 4, '复核通过', 4, 'admin',
 '复核通过：现场压力、浓度复测合格，设备运行正常。', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(6025, 6003, 'WX20260920083003-0003', 5, '已封卡', 5, 'admin',
 '完工封卡，工单永久封存。', DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 超期预警定时任务：每小时第7分钟扫描超期未封卡的抢修工单，站内通知+邮件提醒负责人（同单24小时内不重复提醒）
INSERT IGNORE INTO t_sys_quartz_job (id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (9003, '抢修工单超期预警', 'GASNET', 'gasRepairOrderWarnTask.scanOverdue()', '0 7 * * * ?', '2', '1', 0, 'seed', NOW(), '每小时扫描超过办结时限仍未完工封卡的抢修工单，通知负责人督促尽快完成，同单24小时内不重复提醒');
