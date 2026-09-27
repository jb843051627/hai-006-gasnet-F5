package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasConcRule;

/**
 * 泄漏浓度分档判定线 Service接口（rule-eval 形状：规则求值 + 版本配置）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGasConcRuleService {

    /** 按主键查询规则版本 */
    TGasConcRule selectTGasConcRuleById(Long id);

    /** 台账查询（含历史版本，按 rule_code、version 排序由调用方/默认顺序给出） */
    List<TGasConcRule> selectTGasConcRuleList(QueryWrapper<TGasConcRule> queryWrapper);

    /**
     * 定位某代号在 at 时刻"在用"的规则版本：
     * del_flag=0、status=0(现行)、eff_start &lt;= at &lt; eff_end(eff_end 为空视为长期有效)；
     * 同一时刻理论上仅一个版本，命中多个时取 eff_start 最晚者。无命中返回 null。
     */
    TGasConcRule effectiveAt(String ruleCode, Date at);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1轻微 2中度 3严重 4极严重）。
     * 无生效版本 / 输入为负 / 参数缺失一律返回 0（无法判定）。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中优先级最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /**
     * 按已定位的规则版本判档（旁路：调用方已拿到版本实体时复用，口径须与 evaluate 一致）。
     * rule 为 null 返回 0；等于上限取高一档。
     */
    int gradeBy(TGasConcRule rule, BigDecimal input);

    /** at 时刻可用规则（优先级降序、ruleCode 降序） */
    List<TGasConcRule> listAvailable(Date at);

    /** 单条规则版本在 at 时刻是否在用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻在用规则版本条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /**
     * 新建规则代号并发布首个版本（version=1，status=0现行）。
     * 阈值须满足 0 &lt; th1 &lt; th2 &lt; th3，effStart 为空取当前时刻。
     * 代号已存在 / 阈值不合法抛 IllegalArgumentException。
     */
    int insertTGasConcRule(TGasConcRule rule);

    /**
     * 为已有 rule_code 发布新版本：version 自增；
     * 自动把被接替的现行版本交棒（eff_end=新版 eff_start、status=1已让位），
     * 与其它版本时间窗重叠时拒绝。
     */
    int publishNewVersion(TGasConcRule rule);

    /**
     * 编辑规则版本：未生效版本可改阈值/生效日；已生效版本仅可改名称、优先级、备注
     * （阈值变更必须走"发布新版本"，保证历史评定可追溯）。
     */
    int updateTGasConcRule(TGasConcRule rule);

    /** 停用（立即交棒）：status=1已让位、eff_end=当前时刻；effEnd 可指定（须晚于 eff_start） */
    int retire(Long id, Date effEnd, String updateBy);

    /**
     * 交棒排期翻转：把仍标记为"现行"但 eff_end 已到（&lt;= at）的版本置为"已让位"。
     * 供定时任务周期调用，返回翻转条数；评定口径本身只认时间窗，此翻转仅用于台账状态位准确。
     */
    int rolloverScheduled(Date at);

    /** 逻辑删除（del_flag=1）；已产生的评定记录保留版本快照不受影响 */
    int deleteTGasConcRuleByIds(String ids);
}
