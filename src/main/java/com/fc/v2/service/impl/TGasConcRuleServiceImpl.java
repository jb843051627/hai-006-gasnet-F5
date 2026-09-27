package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TGasConcRuleMapper;
import com.fc.v2.model.auto.TGasConcRule;
import com.fc.v2.service.ITGasConcRuleService;

/**
 * 泄漏浓度分档判定线 Service业务层处理（rule-eval 形状：版本化规则求值 + 配置）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGasConcRuleServiceImpl implements ITGasConcRuleService {

    /** 判线情形：现行 */
    private static final int STATUS_CURRENT = 0;
    /** 判线情形：已让位 */
    private static final int STATUS_RETIRED = 1;
    /** 删除标记：正常 */
    private static final int DEL_NORMAL = 0;
    /** 删除标记：删除 */
    private static final int DEL_DELETED = 1;

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TGasConcRule> PRIORITY_THEN_CODE_DESC = new Comparator<TGasConcRule>() {
        @Override
        public int compare(TGasConcRule a, TGasConcRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TGasConcRuleMapper gasConcRuleMapper;

    @Override
    public TGasConcRule selectTGasConcRuleById(Long id) {
        return this.gasConcRuleMapper.selectById(id);
    }

    @Override
    public List<TGasConcRule> selectTGasConcRuleList(QueryWrapper<TGasConcRule> queryWrapper) {
        return this.gasConcRuleMapper.selectList(queryWrapper);
    }

    /**
     * 时段判定统一走内存 {@link Date#compareTo(Date)}：
     * JDBC 的 serverTimezone 与本机时区不对称时，直接把 java.util.Date 作绑定参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档。规则为配置级数据量，整表过滤可接受。
     */
    @Override
    public TGasConcRule effectiveAt(String ruleCode, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        // 注意：此处**不能**带 status=0 条件——旧版交棒后状态已翻转为"已让位"，
        // 但复核历史检测（如 2025 年的记录在 2026 年重算）必须凭时间窗找回当时版本。
        // "当时是否在用"只取决于 [eff_start, eff_end)；status 仅为台账展示位。
        List<TGasConcRule> rows = this.gasConcRuleMapper.selectList(new QueryWrapper<TGasConcRule>()
                .eq("del_flag", DEL_NORMAL)
                .eq("rule_code", ruleCode));
        TGasConcRule hit = null;
        for (TGasConcRule r : rows) {
            if (isExistingRow(r) && inWindow(r, at) && (hit == null || laterStart(r, hit))) {
                hit = r;
            }
        }
        return hit;
    }

    /** 内存侧再兜一层 del_flag（SQL 已带条件，旁路/单测口径一致）；status 不过滤以支持历史版本回溯 */
    private static boolean isExistingRow(TGasConcRule r) {
        return r.getDelFlag() == null || r.getDelFlag() == DEL_NORMAL;
    }

    /** at 落在 [eff_start, eff_end)；eff_end 为空视为长期有效；eff_start 为空视为不限下限 */
    private static boolean inWindow(TGasConcRule r, Date at) {
        if (r.getEffStart() != null && at.compareTo(r.getEffStart()) < 0) {
            return false;
        }
        if (r.getEffEnd() != null && at.compareTo(r.getEffEnd()) >= 0) {
            return false;
        }
        return true;
    }

    /** 命中多个版本时取生效日晚者 */
    private static boolean laterStart(TGasConcRule a, TGasConcRule b) {
        if (a.getEffStart() == null) {
            return false;
        }
        if (b.getEffStart() == null) {
            return true;
        }
        return a.getEffStart().compareTo(b.getEffStart()) > 0;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (input == null || input.compareTo(BigDecimal.ZERO) < 0) {
            return 0;
        }
        return gradeBy(effectiveAt(ruleCode, at), input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null || input.compareTo(BigDecimal.ZERO) < 0) {
            return 0;
        }
        List<TGasConcRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return gradeBy(avail.get(0), input);
    }

    @Override
    public List<TGasConcRule> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TGasConcRule>();
        }
        // 同样不带 status 条件：在否在用只看时间窗（排期翻转延迟时仍能选出版本）
        List<TGasConcRule> rows = this.gasConcRuleMapper.selectList(new QueryWrapper<TGasConcRule>()
                .eq("del_flag", DEL_NORMAL));
        List<TGasConcRule> avail = new ArrayList<TGasConcRule>();
        for (TGasConcRule r : rows) {
            if (isExistingRow(r) && inWindow(r, at)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TGasConcRule r = this.gasConcRuleMapper.selectById(id);
        // 在否在用只看时间窗；已让位版本在其历史窗口内的时刻仍算"当时在用"（复核口径）
        return r != null && isExistingRow(r) && inWindow(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    @Override
    public int insertTGasConcRule(TGasConcRule rule) {
        String code = requireCode(rule);
        if (rule.getEffStart() == null) {
            rule.setEffStart(new Date());
        }
        validateThresholds(rule);
        Integer count = this.gasConcRuleMapper.selectCount(new QueryWrapper<TGasConcRule>()
                .eq("del_flag", DEL_NORMAL)
                .eq("rule_code", code));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("规则代号已存在：" + code + "，调整阈值请发布新版本");
        }
        rule.setVersion(1);
        rule.setStatus(STATUS_CURRENT);
        rule.setEffEnd(null);
        rule.setDelFlag(DEL_NORMAL);
        return this.gasConcRuleMapper.insert(rule);
    }

    @Override
    public int publishNewVersion(TGasConcRule rule) {
        String code = requireCode(rule);
        if (rule.getEffStart() == null) {
            rule.setEffStart(new Date());
        }
        validateThresholds(rule);
        List<TGasConcRule> versions = this.gasConcRuleMapper.selectList(new QueryWrapper<TGasConcRule>()
                .eq("del_flag", DEL_NORMAL)
                .eq("rule_code", code)
                .orderByDesc("version"));
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("规则代号不存在：" + code + "，请先新建规则");
        }
        int maxVersion = 0;
        for (TGasConcRule v : versions) {
            if (v.getVersion() != null && v.getVersion() > maxVersion) {
                maxVersion = v.getVersion();
            }
        }
        // 新版生效日 S 的合法性（同一代号时间轴须形成不相交链条）：
        //  - 存在生效日晚于 S 的版本（未来排期版）→ 拒绝，避免覆盖已排期计划；
        //  - 已让位版本的窗口仍覆盖 S（封存历史）→ 拒绝，历史不可重写；
        //  - 现行版本窗口覆盖 S → 允许，由本次发布自动交棒收尾。
        for (TGasConcRule v : versions) {
            Date vs = v.getEffStart();
            if (vs != null && vs.compareTo(rule.getEffStart()) > 0) {
                throw new IllegalArgumentException("已存在生效日更晚的排期版本 V" + v.getVersion()
                        + "，请先调整或撤销该版本");
            }
            boolean covers = inWindow(v, rule.getEffStart());
            int st = v.getStatus() == null ? STATUS_CURRENT : v.getStatus();
            if (covers && st == STATUS_RETIRED) {
                throw new IllegalArgumentException("生效时间落入已让位版本 V" + v.getVersion()
                        + " 的封存区间，历史版本不可重写");
            }
        }
        rule.setVersion(maxVersion + 1);
        rule.setStatus(STATUS_CURRENT);
        rule.setEffEnd(null);
        rule.setDelFlag(DEL_NORMAL);
        int rows = this.gasConcRuleMapper.insert(rule);

        // 自动交棒：凡与新版时间窗相接的旧版本，eff_end 收到新版生效日；
        // 新版即刻生效 → 旧版立即置"已让位"；新版未来生效 → 旧版保留"现行"、仅排期交棒，
        // 期间仍按旧版评定，避免青黄不接（状态位滞后无妨，在否在用以时间窗为准）。
        boolean effectiveNow = rule.getEffStart().compareTo(new Date()) <= 0;
        for (TGasConcRule v : versions) {
            if (v.getStatus() != null && v.getStatus() == STATUS_CURRENT && inWindow(v, rule.getEffStart())) {
                v.setEffEnd(rule.getEffStart());
                v.setStatus(effectiveNow ? STATUS_RETIRED : STATUS_CURRENT);
                this.gasConcRuleMapper.updateById(v);
            }
        }
        return rows;
    }

    @Override
    public int updateTGasConcRule(TGasConcRule rule) {
        if (rule.getId() == null) {
            throw new IllegalArgumentException("缺少规则主键");
        }
        TGasConcRule db = this.gasConcRuleMapper.selectById(rule.getId());
        if (db == null || (db.getDelFlag() != null && db.getDelFlag() == DEL_DELETED)) {
            return 0;
        }
        // 是否"已生效过"只看生效日是否已到（含已让位的封存版本），与 status 无关：
        // 凡已到生效日的版本，阈值/生效日一律冻结，保证历史评定可追溯复算。
        boolean alreadyEffective = db.getEffStart() != null
                && db.getEffStart().compareTo(new Date()) <= 0;
        if (alreadyEffective) {
            // 已生效版本冻结阈值与生效日，仅允许名称/优先级/备注随需变更
            db.setRuleName(rule.getRuleName());
            db.setPriority(rule.getPriority());
            db.setRemark(rule.getRemark());
            return this.gasConcRuleMapper.updateById(db);
        }
        validateThresholds(rule);
        if (rule.getEffStart() != null) {
            // 生效日改动同样不得落入其它版本的封存区间，也不得越过更晚的排期版本
            List<TGasConcRule> siblings = this.gasConcRuleMapper.selectList(new QueryWrapper<TGasConcRule>()
                    .eq("del_flag", DEL_NORMAL)
                    .eq("rule_code", db.getRuleCode()));
            for (TGasConcRule v : siblings) {
                if (v.getId().equals(db.getId())) {
                    continue;
                }
                if (v.getEffStart() != null && v.getEffStart().compareTo(rule.getEffStart()) > 0) {
                    throw new IllegalArgumentException("存在生效日更晚的排期版本 V" + v.getVersion()
                            + "，生效日不得越过它");
                }
                int st = v.getStatus() == null ? STATUS_CURRENT : v.getStatus();
                if (st == STATUS_RETIRED && inWindow(v, rule.getEffStart())) {
                    throw new IllegalArgumentException("生效时间落入已让位版本 V" + v.getVersion()
                            + " 的封存区间");
                }
            }
            db.setEffStart(rule.getEffStart());
        }
        db.setRuleName(rule.getRuleName());
        db.setTh1Max(rule.getTh1Max());
        db.setTh2Max(rule.getTh2Max());
        db.setTh3Max(rule.getTh3Max());
        db.setPriority(rule.getPriority());
        db.setRemark(rule.getRemark());
        return this.gasConcRuleMapper.updateById(db);
    }

    @Override
    public int retire(Long id, Date effEnd, String updateBy) {
        TGasConcRule r = this.gasConcRuleMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == DEL_DELETED)) {
            return 0;
        }
        Date end = effEnd != null ? effEnd : new Date();
        if (r.getEffStart() != null && end.compareTo(r.getEffStart()) <= 0) {
            throw new IllegalArgumentException("交棒时间必须晚于启用之日");
        }
        r.setEffEnd(end);
        // 立即交棒才置"已让位"；未来时刻交棒则保留"现行"排期，避免在否在用与时间窗脱节
        r.setStatus(end.compareTo(new Date()) <= 0 ? STATUS_RETIRED : STATUS_CURRENT);
        r.setUpdateBy(updateBy);
        r.setUpdateTime(new Date());
        return this.gasConcRuleMapper.updateById(r);
    }

    @Override
    public int rolloverScheduled(Date at) {
        if (at == null) {
            return 0;
        }
        List<TGasConcRule> scheduled = this.gasConcRuleMapper.selectList(new QueryWrapper<TGasConcRule>()
                .eq("del_flag", DEL_NORMAL)
                .eq("status", STATUS_CURRENT)
                .isNotNull("eff_end"));
        int rows = 0;
        for (TGasConcRule r : scheduled) {
            if (r.getEffEnd() != null && r.getEffEnd().compareTo(at) <= 0) {
                r.setStatus(STATUS_RETIRED);
                r.setUpdateTime(new Date());
                rows += this.gasConcRuleMapper.updateById(r);
            }
        }
        return rows;
    }

    @Override
    public int deleteTGasConcRuleByIds(String ids) {
        if (ids == null || ids.trim().isEmpty()) {
            return 0;
        }
        int rows = 0;
        Date now = new Date();
        for (String s : ConvertUtil.toStrArray(ids)) {
            Long id = Long.valueOf(s.trim());
            TGasConcRule r = this.gasConcRuleMapper.selectById(id);
            if (r == null) {
                continue;
            }
            r.setDelFlag(DEL_DELETED);
            // 现行版本一并交棒，避免仍被 effectiveAt 命中（查询均带 del_flag=0）
            if (r.getStatus() != null && r.getStatus() == STATUS_CURRENT) {
                r.setStatus(STATUS_RETIRED);
                if (r.getEffEnd() == null) {
                    r.setEffEnd(now);
                }
            }
            rows += this.gasConcRuleMapper.updateById(r);
        }
        return rows;
    }

    private static String requireCode(TGasConcRule rule) {
        if (rule == null || rule.getRuleCode() == null || rule.getRuleCode().trim().isEmpty()) {
            throw new IllegalArgumentException("规则代号不能为空");
        }
        return rule.getRuleCode().trim();
    }

    /** 阈值合法性：非空、非负且严格递增 0 <= th1 < th2 < th3 */
    private static void validateThresholds(TGasConcRule r) {
        BigDecimal t1 = r.getTh1Max();
        BigDecimal t2 = r.getTh2Max();
        BigDecimal t3 = r.getTh3Max();
        if (t1 == null || t2 == null || t3 == null) {
            throw new IllegalArgumentException("三档浓度上限均不能为空");
        }
        if (t1.compareTo(BigDecimal.ZERO) < 0
                || t1.compareTo(t2) >= 0
                || t2.compareTo(t3) >= 0) {
            throw new IllegalArgumentException("阈值须满足 0 ≤ 轻度上限 < 中度上限 < 重度上限");
        }
    }

    /**
     * 分档：浓度等于上限时取高一档（与管线长度分档口径一致）。
     * 即 input &lt;= th1 → 轻微(1) 由旧口径修正为 input == th1 落中度(2)。
     * 档位：1轻微(0,t1] ... 4极严重(t3,+∞)；阈值缺失的档跳过。
     */
    @Override
    public int gradeBy(TGasConcRule rule, BigDecimal input) {
        if (rule == null || input == null) {
            return 0;
        }
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
