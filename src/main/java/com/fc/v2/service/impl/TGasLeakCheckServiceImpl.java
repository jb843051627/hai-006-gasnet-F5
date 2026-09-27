package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGasLeakCheckMapper;
import com.fc.v2.model.auto.TGasConcRule;
import com.fc.v2.model.auto.TGasLeakCheck;
import com.fc.v2.service.ITGasConcRuleService;
import com.fc.v2.service.ITGasLeakCheckService;

/**
 * 泄漏浓度检测评定记录 Service业务层处理
 * （grade-eval 形状：以检测时刻在用的规则版本判档，版本快照留痕，支持事后按原始检测时间复核重算）
 *
 * @author fuce
 * @date 2026-09-26
 */
@Service
public class TGasLeakCheckServiceImpl implements ITGasLeakCheckService {

    /** 复核情形：未复核 / 已重算 / 重算后等级变更 */
    private static final int RECHECK_NONE = 0;
    private static final int RECHECK_DONE = 1;
    private static final int RECHECK_CHANGED = 2;

    /** 记录情形：有效 */
    private static final int STATUS_VALID = 0;
    /** 删除标记：正常 */
    private static final int DEL_NORMAL = 0;

    @javax.annotation.Resource
    private TGasLeakCheckMapper gasLeakCheckMapper;

    @javax.annotation.Resource
    private ITGasConcRuleService gasConcRuleService;

    @Override
    public TGasLeakCheck selectTGasLeakCheckById(Long id) {
        return this.gasLeakCheckMapper.selectById(id);
    }

    @Override
    public List<TGasLeakCheck> selectTGasLeakCheckList(QueryWrapper<TGasLeakCheck> queryWrapper) {
        return this.gasLeakCheckMapper.selectList(queryWrapper);
    }

    @Override
    public TGasLeakCheck evaluateAndSave(TGasLeakCheck record, String ruleCode) {
        if (record == null) {
            throw new IllegalArgumentException("检测记录不能为空");
        }
        if (record.getDetectTime() == null) {
            throw new IllegalArgumentException("现场检测时间不能为空（据此定位当时在用规则版本）");
        }
        BigDecimal conc = record.getConcValue();
        if (conc == null || conc.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("浓度读数不能为空且不能为负");
        }

        // 定位检测时刻在用版本：指定代号按代号；否则取优先级最高的可用版本
        TGasConcRule rule;
        if (ruleCode != null && !ruleCode.trim().isEmpty()) {
            rule = gasConcRuleService.effectiveAt(ruleCode.trim(), record.getDetectTime());
        } else {
            List<TGasConcRule> avail = gasConcRuleService.listAvailable(record.getDetectTime());
            rule = avail.isEmpty() ? null : avail.get(0);
        }
        int grade = rule == null ? 0 : gasConcRuleService.gradeBy(rule, conc);

        record.setGradeLevel(grade);
        if (rule != null) {
            record.setRuleId(rule.getId());
            record.setRuleCode(rule.getRuleCode());
            record.setRuleVersion(rule.getVersion());
        }
        record.setRecheckStatus(RECHECK_NONE);
        record.setStatus(STATUS_VALID);
        record.setDelFlag(DEL_NORMAL);
        this.gasLeakCheckMapper.insert(record);
        // 主键(雪花)在 insert 时落定，再补记录号，保证同秒批量录入不撞号
        if (record.getCheckNo() == null || record.getCheckNo().trim().isEmpty()) {
            record.setCheckNo(generateCheckNo(record.getDetectTime(), record.getId()));
            this.gasLeakCheckMapper.updateById(record);
        }
        return record;
    }

    @Override
    public TGasLeakCheck recheck(Long id, String recheckBy, String remark) {
        TGasLeakCheck record = this.gasLeakCheckMapper.selectById(id);
        if (record == null || (record.getDelFlag() != null && record.getDelFlag() == 1)) {
            return null;
        }
        applyRecheck(record, recheckBy, remark);
        this.gasLeakCheckMapper.updateById(record);
        return record;
    }

    @Override
    public int recheckRange(Date beginTime, Date endTime, String recheckBy, String remark) {
        if (beginTime == null || endTime == null || beginTime.after(endTime)) {
            throw new IllegalArgumentException("复核时间段不合法");
        }
        // 时段绑定走墙钟字符串（与规则窗口判定同源），规避 JDBC serverTimezone 偏移
        QueryWrapper<TGasLeakCheck> qw = new QueryWrapper<TGasLeakCheck>()
                .eq("del_flag", DEL_NORMAL)
                .eq("status", STATUS_VALID)
                .ge("detect_time", ts(beginTime))
                .le("detect_time", ts(endTime))
                .orderByAsc("detect_time");
        List<TGasLeakCheck> rows = this.gasLeakCheckMapper.selectList(qw);
        int ok = 0;
        for (TGasLeakCheck r : rows) {
            applyRecheck(r, recheckBy, remark);
            ok += this.gasLeakCheckMapper.updateById(r);
        }
        return ok;
    }

    /**
     * 复核重算口径：**始终以原始检测时间**重新选版判档——
     * 若检测跨越规则变更点，历史记录维持按当时版本评定，不会被现行规则覆盖。
     * 原始 grade_level/rule_* 快照不动，只回写 recheck_*；无在用版本时复核等级记 0。
     */
    private void applyRecheck(TGasLeakCheck record, String recheckBy, String remark) {
        // 原评定记录了代号 → 只在该代号下按原始检测时间重新选版；
        // 该代号当时无在用版本（已停用/删除）记 0，不跨代号回退，避免换错判线。
        TGasConcRule rule = null;
        if (record.getRuleCode() != null && !record.getRuleCode().trim().isEmpty()) {
            rule = gasConcRuleService.effectiveAt(record.getRuleCode(), record.getDetectTime());
        } else {
            List<TGasConcRule> avail = gasConcRuleService.listAvailable(record.getDetectTime());
            rule = avail.isEmpty() ? null : avail.get(0);
        }
        int reGrade = rule == null ? 0 : gasConcRuleService.gradeBy(rule, record.getConcValue());

        record.setRecheckGradeLevel(reGrade);
        record.setRecheckRuleId(rule == null ? null : rule.getId());
        record.setRecheckRuleVersion(rule == null ? null : rule.getVersion());
        Integer origin = record.getGradeLevel();
        record.setRecheckStatus(origin != null && origin != reGrade ? RECHECK_CHANGED : RECHECK_DONE);
        record.setRecheckTime(new Date());
        record.setRecheckBy(recheckBy);
        record.setRecheckRemark(remark);
    }

    /** 记录号：LC + 检测时间(yyyyMMddHHmmss) + 主键末4位（与派发单号 DP... 同风格） */
    private static String generateCheckNo(Date detectTime, Long id) {
        String tail = id == null ? "0000" : String.format("%04d", Math.abs(id % 10000L));
        return "LC" + new SimpleDateFormat("yyyyMMddHHmmss").format(detectTime) + "-" + tail;
    }

    /** 墙钟字符串（yyyy-MM-dd HH:mm:ss），时段绑定专用，规避 JDBC serverTimezone 偏移 */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }
}
