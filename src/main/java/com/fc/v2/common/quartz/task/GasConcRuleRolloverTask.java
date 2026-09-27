package com.fc.v2.common.quartz.task;

import com.fc.v2.service.ITGasConcRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 浓度分档规则交棒排期翻转定时任务。
 * 由 t_sys_quartz_job 中 invoke_target = gasConcRuleRolloverTask.rollover() 触发，
 * 默认每分钟执行一次，把 eff_end 已到的"现行"版本翻转为"已让位"。
 * 评定是否在用以 [eff_start, eff_end) 时间窗为准，本任务只负责台账状态位及时归位。
 *
 * @author fuce
 * @date 2026-09-26
 */
@Component("gasConcRuleRolloverTask")
public class GasConcRuleRolloverTask {

    private static final Logger log = LoggerFactory.getLogger(GasConcRuleRolloverTask.class);

    @Autowired
    private ITGasConcRuleService gasConcRuleService;

    /** 翻转到点交棒的规则版本 */
    public void rollover() {
        int rows = gasConcRuleService.rolloverScheduled(new Date());
        if (rows > 0) {
            log.info("[gasConcRuleRolloverTask] 规则交棒排期翻转完成，本轮翻转 {} 个版本", rows);
        }
    }
}
