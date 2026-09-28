package com.fc.v2.common.quartz.task;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.service.ITGasRepairOrderService;

/**
 * 抢修工单超期预警定时任务。
 * 由 t_sys_quartz_job 中 invoke_target = gasRepairOrderWarnTask.scanOverdue() 触发，
 * 默认每小时整点扫描一次（可在定时任务管理中调整周期）。
 *
 * 扫描已过办结时限仍未封卡、且距上次提醒满24小时的工单，
 * 向负责人（无负责人则建单人）发站内通知并抄送邮件，督促尽快完成处置。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Component("gasRepairOrderWarnTask")
public class GasRepairOrderWarnTask {

    private static final Logger log = LoggerFactory.getLogger(GasRepairOrderWarnTask.class);

    @Autowired
    private ITGasRepairOrderService gasRepairOrderService;

    /**
     * 扫描超期未办结的抢修工单并预警提醒
     */
    public void scanOverdue() {
        int warned = gasRepairOrderService.scanOverdue(new Date());
        log.info("[gasRepairOrderWarnTask] 超期预警扫描完成，本轮预警抢修工单 {} 张", warned);
    }
}
