package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasLeakCheck;

/**
 * 泄漏浓度检测评定记录 Service接口（grade-eval 形状：按检测时刻的规则版本判档并留痕）
 *
 * @author fuce
 * @date 2026-09-26
 */
public interface ITGasLeakCheckService {

    /** 按主键查询评定记录 */
    TGasLeakCheck selectTGasLeakCheckById(Long id);

    /** 台账查询（时间段、检测点、等级过滤由调用方拼 QueryWrapper） */
    List<TGasLeakCheck> selectTGasLeakCheckList(QueryWrapper<TGasLeakCheck> queryWrapper);

    /**
     * 录入一次检测并完成评定：
     * 以 detectTime 定位 ruleCode 当时"在用"的规则版本（不传 ruleCode 则取优先级最高的可用版本），
     * 记录原始浓度、等级、规则版本快照（id/code/version）。
     * 当时无在用版本时等级记 0（无法判定），规则快照为空，记录照常入库等待复核。
     * 检测时间缺失或浓度为负抛 IllegalArgumentException。
     */
    TGasLeakCheck evaluateAndSave(TGasLeakCheck record, String ruleCode);

    /**
     * 单条复核重算：**始终以记录的原始检测时间**重新定位当时在用版本判档，
     * 不覆盖原始评定结果，仅回写 recheck_* 字段；等级与原档不一致时 recheckStatus=2。
     * 规则版本被删除/停用时仍可凭快照展示原结论。
     */
    TGasLeakCheck recheck(Long id, String recheckBy, String remark);

    /**
     * 按时间段批量复核重算：detect_time 落在 [beginTime, endTime] 的有效记录逐条重算，
     * 返回成功重算条数。
     */
    int recheckRange(Date beginTime, Date endTime, String recheckBy, String remark);
}
