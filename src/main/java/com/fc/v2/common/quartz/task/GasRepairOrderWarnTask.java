package com.fc.v2.common.quartz.task;

import java.util.Date;

import com.fc.v2.service.ITGasRepairOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 抢修工单超期预警定时任务。
 * 由 t_sys_quartz_job 中 invoke_target = gasRepairOrderWarnTask.scanOverdue() 触发，
 * 默认每小时整点执行一次（可在定时任务管理中调整周期）。
 *
 * 扫描处置时限已过仍未完工封卡的工单，向负责人/接单巡检员发站内信督促处置；
 * 同一工单 24 小时内不重复提醒。
 *
 * @author fuce
 * @date 2026-09-28
 */
@Component("gasRepairOrderWarnTask")
public class GasRepairOrderWarnTask {

    private static final Logger log = LoggerFactory.getLogger(GasRepairOrderWarnTask.class);

    @Autowired
    private ITGasRepairOrderService gasRepairOrderService;

    /**
     * 扫描超期未封卡的抢修工单并预警督促
     */
    public void scanOverdue() {
        int warned = gasRepairOrderService.scanOverdue(new Date());
        log.info("[gasRepairOrderWarnTask] 抢修工单超期预警扫描完成，本轮预警 {} 单", warned);
    }
}
