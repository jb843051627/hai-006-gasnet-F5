package com.fc.v2.common.quartz.task;

import com.fc.v2.service.ITGasPlanTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 燃气巡检到期任务扫描派单定时任务。
 * 由 t_sys_quartz_job 中 invoke_target = gasPlanTaskScanTask.scanDueTasks() 触发，
 * 默认每日 01:00 执行一次（可在定时任务管理中调整周期）。
 *
 * @author fuce
 * @date 2026-09-26
 */
@Component("gasPlanTaskScanTask")
public class GasPlanTaskScanTask {

    private static final Logger log = LoggerFactory.getLogger(GasPlanTaskScanTask.class);

    @Autowired
    private ITGasPlanTaskService gasPlanTaskService;

    /**
     * 扫描应派未派的到期任务，按班组负载均衡自动派单（超期3天以上优先）
     */
    public void scanDueTasks() {
        int ok = gasPlanTaskService.runOnce(new Date());
        log.info("[gasPlanTaskScanTask] 到期任务扫描完成，本轮成功派单 {} 条", ok);
    }
}
