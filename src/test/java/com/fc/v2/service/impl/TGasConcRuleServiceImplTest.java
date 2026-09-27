package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGasConcRuleMapper;
import com.fc.v2.model.auto.TGasConcRule;

/**
 * 浓度分档规则：按时间窗口选版、判档边界、版本发布交棒的单元测试
 */
@ExtendWith(MockitoExtension.class)
class TGasConcRuleServiceImplTest {

    @Mock
    private TGasConcRuleMapper gasConcRuleMapper;

    @InjectMocks
    private TGasConcRuleServiceImpl service;

    private static Date at(String ts) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ts);
    }

    private static TGasConcRule rule(long id, String code, int version,
                                    String t1, String t2, String t3,
                                    String start, String end, int status, Integer priority)
            throws Exception {
        TGasConcRule r = new TGasConcRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setRuleName(code + "判线");
        r.setVersion(version);
        if (t1 != null) {
            r.setTh1Max(new BigDecimal(t1));
        }
        if (t2 != null) {
            r.setTh2Max(new BigDecimal(t2));
        }
        if (t3 != null) {
            r.setTh3Max(new BigDecimal(t3));
        }
        r.setEffStart(start == null ? null : at(start));
        r.setEffEnd(end == null ? null : at(end));
        r.setStatus(status);
        r.setDelFlag(0);
        r.setPriority(priority);
        return r;
    }

    /** 让 mapper 按 rule_code 过滤返回（实现用 eq 条件查库，测试侧直接给全量代号行） */
    private void mockRules(TGasConcRule... rules) {
        when(gasConcRuleMapper.selectList(any())).thenReturn(new ArrayList<TGasConcRule>(Arrays.asList(rules)));
    }

    // ---------- effectiveAt / evaluate：跨越规则变更点按当时版本 ----------

    @Test
    void evaluate_picksVersionByDetectTime_notCurrentOne() throws Exception {
        TGasConcRule v1 = rule(8001, "CITY-MID", 1, "20", "50", "100",
                "2025-01-01 00:00:00", "2026-01-01 00:00:00", 1, 10);
        TGasConcRule v2 = rule(8002, "CITY-MID", 2, "10", "30", "80",
                "2026-01-01 00:00:00", null, 0, 10);
        mockRules(v1, v2);

        // 同一读数 15ppm：交棒前按 V1 → 轻微(1)
        assertEquals(1, service.evaluate("CITY-MID", new BigDecimal("15"), at("2025-12-15 10:00:00")));
        // 交棒后按 V2 → 中度(2)
        assertEquals(2, service.evaluate("CITY-MID", new BigDecimal("15"), at("2026-09-20 10:30:00")));
    }

    @Test
    void effectiveAt_windowIsLeftClosedRightOpen() throws Exception {
        TGasConcRule v1 = rule(8001, "C", 1, "20", "50", "100",
                "2025-01-01 00:00:00", "2026-01-01 00:00:00", 1, 10);
        TGasConcRule v2 = rule(8002, "C", 2, "10", "30", "80",
                "2026-01-01 00:00:00", null, 0, 10);
        mockRules(v1, v2);

        // 恰为 eff_end（不含）→ V1 已不可用，落 V2
        assertEquals(8002L, service.effectiveAt("C", at("2026-01-01 00:00:00")).getId());
        // 恰为 eff_start（含）→ V1
        assertEquals(8001L, service.effectiveAt("C", at("2025-01-01 00:00:00")).getId());
        // eff_end 前一秒仍 V1
        assertEquals(8001L, service.effectiveAt("C", at("2025-12-31 23:59:59")).getId());
    }

    @Test
    void evaluate_ignoresDeletedAndRetiredAndOutsideWindow() throws Exception {
        TGasConcRule deleted = rule(1, "C", 1, "10", "30", "80",
                "2025-01-01 00:00:00", null, 0, 10);
        deleted.setDelFlag(1);
        TGasConcRule retired = rule(2, "C", 2, "10", "30", "80",
                "2025-01-01 00:00:00", "2026-01-01 00:00:00", 1, 10);
        TGasConcRule future = rule(3, "C", 3, "10", "30", "80",
                "2027-01-01 00:00:00", null, 0, 10);
        mockRules(deleted, retired, future);

        // 2026 年中：已删除/已让位/未生效都不可用
        assertNull(service.effectiveAt("C", at("2026-06-01 00:00:00")));
        assertEquals(0, service.evaluate("C", new BigDecimal("15"), at("2026-06-01 00:00:00")));
        // 2027 年：未来版生效
        assertEquals(3L, service.effectiveAt("C", at("2027-06-01 00:00:00")).getId());
    }

    // ---------- gradeBy：等于上限取高一档 ----------

    @Test
    void gradeBy_equalThresholdGoesHigherTier() throws Exception {
        TGasConcRule r = rule(1, "C", 1, "10", "30", "80",
                "2025-01-01 00:00:00", null, 0, 10);
        assertEquals(1, service.gradeBy(r, new BigDecimal("9.99")));
        assertEquals(2, service.gradeBy(r, new BigDecimal("10.00")));   // 等于轻度上限 → 中度
        assertEquals(2, service.gradeBy(r, new BigDecimal("29.99")));
        assertEquals(3, service.gradeBy(r, new BigDecimal("30.00")));   // 等于中度上限 → 严重
        assertEquals(3, service.gradeBy(r, new BigDecimal("80.00") .subtract(new BigDecimal("0.01"))));
        assertEquals(4, service.gradeBy(r, new BigDecimal("80.00")));   // 等于严重上限 → 极严重
        assertEquals(4, service.gradeBy(r, new BigDecimal("999")));
        assertEquals(0, service.gradeBy(null, new BigDecimal("1")));
    }

    @Test
    void evaluate_invalidArgsReturnZero() throws Exception {
        // 参数校验在查库之前短路，无需打桩（strict stubbing 下空打桩会报错）
        assertEquals(0, service.evaluate("C", new BigDecimal("-1"), at("2026-01-01 00:00:00")));
        assertEquals(0, service.evaluate("C", null, at("2026-01-01 00:00:00")));
        assertEquals(0, service.evaluate("", new BigDecimal("1"), at("2026-01-01 00:00:00")));
        assertEquals(0, service.evaluate("C", new BigDecimal("1"), null));
    }

    // ---------- listAvailable：优先级排序 ----------

    @Test
    void listAvailable_sortedByPriorityDescThenCodeDesc() throws Exception {
        TGasConcRule low = rule(1, "AA", 1, "10", "30", "80",
                "2025-01-01 00:00:00", null, 0, 5);
        TGasConcRule high = rule(2, "BB", 1, "10", "30", "80",
                "2025-01-01 00:00:00", null, 0, 99);
        TGasConcRule tieZ = rule(3, "ZZ", 1, "10", "30", "80",
                "2025-01-01 00:00:00", null, 0, 99);
        mockRules(low, high, tieZ);

        List<TGasConcRule> avail = service.listAvailable(at("2026-06-01 00:00:00"));
        assertEquals(3, avail.size());
        // 优先级并列 99：rule_code 降序 ZZ 在 BB 前；低优先级 AA 最后
        assertEquals(Arrays.asList("ZZ", "BB", "AA"),
                Arrays.asList(avail.get(0).getRuleCode(), avail.get(1).getRuleCode(), avail.get(2).getRuleCode()));
        // evaluateTop 取优先级最高者判档
        assertEquals(3, service.evaluateTop(new BigDecimal("50"), at("2026-06-01 00:00:00")));
        assertEquals(3, service.countAvailable(at("2026-06-01 00:00:00")));
        // usable 走 selectById：窗口内的版本可用，已交棒版本在其窗口内时刻仍算"当时在用"
        when(gasConcRuleMapper.selectById(3L)).thenReturn(tieZ);
        assertTrue(service.usable(3L, at("2026-06-01 00:00:00")));
        assertFalse(service.usable(999L, at("2026-06-01 00:00:00")));
    }

    // ---------- 版本发布与自动交棒 ----------

    @Test
    void insert_firstVersion_setsDefaults() {
        when(gasConcRuleMapper.selectCount(any())).thenReturn(0);
        when(gasConcRuleMapper.insert(any())).thenReturn(1);

        TGasConcRule r = new TGasConcRule();
        r.setRuleCode("CITY-MID");
        r.setTh1Max(new BigDecimal("10"));
        r.setTh2Max(new BigDecimal("30"));
        r.setTh3Max(new BigDecimal("80"));
        assertEquals(1, service.insertTGasConcRule(r));
        assertEquals(1, r.getVersion());
        assertEquals(0, r.getStatus());
        assertNull(r.getEffEnd());
        assertEquals(0, r.getDelFlag());
        // 生效日缺省取当前
        assertTrue(r.getEffStart() != null);
    }

    @Test
    void insert_duplicateCode_rejected() {
        when(gasConcRuleMapper.selectCount(any())).thenReturn(1);
        TGasConcRule r = new TGasConcRule();
        r.setRuleCode("C");
        r.setTh1Max(new BigDecimal("10"));
        r.setTh2Max(new BigDecimal("30"));
        r.setTh3Max(new BigDecimal("80"));
        assertThrows(IllegalArgumentException.class, () -> service.insertTGasConcRule(r));
        verify(gasConcRuleMapper, never()).insert(any());
    }

    @Test
    void insert_nonIncreasingThresholds_rejected() {
        TGasConcRule r = new TGasConcRule();
        r.setRuleCode("C");
        r.setTh1Max(new BigDecimal("30"));
        r.setTh2Max(new BigDecimal("30"));
        r.setTh3Max(new BigDecimal("80"));
        assertThrows(IllegalArgumentException.class, () -> service.insertTGasConcRule(r));
    }

    @Test
    void publishNewVersion_closesCurrentVersionAndIncrementsVersion() throws Exception {
        TGasConcRule v1 = rule(8001, "C", 1, "20", "50", "100",
                "2025-01-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectList(any())).thenReturn(new ArrayList<TGasConcRule>(Collections.singletonList(v1)));
        when(gasConcRuleMapper.insert(any())).thenReturn(1);
        when(gasConcRuleMapper.updateById(any())).thenReturn(1);

        TGasConcRule v2Form = new TGasConcRule();
        v2Form.setRuleCode("C");
        v2Form.setTh1Max(new BigDecimal("10"));
        v2Form.setTh2Max(new BigDecimal("30"));
        v2Form.setTh3Max(new BigDecimal("80"));
        v2Form.setEffStart(at("2026-01-01 00:00:00"));
        assertEquals(1, service.publishNewVersion(v2Form));
        assertEquals(2, v2Form.getVersion());

        ArgumentCaptor<TGasConcRule> captor = ArgumentCaptor.forClass(TGasConcRule.class);
        verify(gasConcRuleMapper).updateById(captor.capture());
        TGasConcRule closed = captor.getValue();
        assertEquals(8001L, closed.getId());
        assertEquals(at("2026-01-01 00:00:00"), closed.getEffEnd());
        assertEquals(1, closed.getStatus());
    }

    @Test
    void publishNewVersion_immediatelyEffective_closesLongRunningCurrent() throws Exception {
        // 现行版长期有效，新版立即生效：允许（自动交棒），不视为冲突
        TGasConcRule v1 = rule(8001, "C", 1, "20", "50", "100",
                "2025-01-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectList(any())).thenReturn(new ArrayList<TGasConcRule>(Collections.singletonList(v1)));
        when(gasConcRuleMapper.insert(any())).thenReturn(1);
        when(gasConcRuleMapper.updateById(any())).thenReturn(1);

        TGasConcRule form = new TGasConcRule();
        form.setRuleCode("C");
        form.setTh1Max(new BigDecimal("10"));
        form.setTh2Max(new BigDecimal("30"));
        form.setTh3Max(new BigDecimal("80"));
        form.setEffStart(new Date());
        assertEquals(1, service.publishNewVersion(form));
        assertEquals(2, form.getVersion());
        // 旧版即刻交棒
        assertEquals(1, v1.getStatus());
    }

    @Test
    void publishNewVersion_sealedWindowOverlap_rejected() throws Exception {
        // 已让位版本的封存区间仍覆盖新生效日 → 拒绝（历史不可重写）
        TGasConcRule sealed = rule(8000, "C", 1, "20", "50", "100",
                "2025-01-01 00:00:00", "2026-01-01 00:00:00", 1, 10);
        TGasConcRule current = rule(8001, "C", 2, "10", "30", "80",
                "2026-01-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectList(any()))
                .thenReturn(new ArrayList<TGasConcRule>(Arrays.asList(sealed, current)));

        TGasConcRule form = new TGasConcRule();
        form.setRuleCode("C");
        form.setTh1Max(new BigDecimal("10"));
        form.setTh2Max(new BigDecimal("30"));
        form.setTh3Max(new BigDecimal("80"));
        form.setEffStart(at("2025-06-01 00:00:00")); // 落入 V1 封存区间
        assertThrows(IllegalArgumentException.class, () -> service.publishNewVersion(form));
    }

    @Test
    void publishNewVersion_laterScheduledVersionExists_rejected() throws Exception {
        // 已存在生效日更晚的排期版本 → 拒绝，避免覆盖已排期计划
        TGasConcRule current = rule(8001, "C", 1, "20", "50", "100",
                "2025-01-01 00:00:00", "2027-01-01 00:00:00", 0, 10);
        TGasConcRule scheduled = rule(8002, "C", 2, "10", "30", "80",
                "2027-01-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectList(any()))
                .thenReturn(new ArrayList<TGasConcRule>(Arrays.asList(current, scheduled)));

        TGasConcRule form = new TGasConcRule();
        form.setRuleCode("C");
        form.setTh1Max(new BigDecimal("10"));
        form.setTh2Max(new BigDecimal("30"));
        form.setTh3Max(new BigDecimal("80"));
        form.setEffStart(at("2026-06-01 00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> service.publishNewVersion(form));
    }

    @Test
    void publishNewVersion_unknownCode_rejected() {
        when(gasConcRuleMapper.selectList(any())).thenReturn(Collections.<TGasConcRule>emptyList());
        TGasConcRule form = new TGasConcRule();
        form.setRuleCode("NOPE");
        form.setTh1Max(new BigDecimal("10"));
        form.setTh2Max(new BigDecimal("30"));
        form.setTh3Max(new BigDecimal("80"));
        assertThrows(IllegalArgumentException.class, () -> service.publishNewVersion(form));
    }

    // ---------- 已生效版本阈值冻结 ----------

    @Test
    void update_alreadyEffectiveVersion_freezesThresholds() throws Exception {
        TGasConcRule db = rule(8001, "C", 1, "20", "50", "100",
                "2025-01-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectById(8001L)).thenReturn(db);
        when(gasConcRuleMapper.updateById(any())).thenReturn(1);

        TGasConcRule form = new TGasConcRule();
        form.setId(8001L);
        form.setRuleName("改名");
        form.setTh1Max(new BigDecimal("1"));   // 试图收紧阈值
        form.setTh2Max(new BigDecimal("2"));
        form.setTh3Max(new BigDecimal("3"));
        form.setPriority(99);
        service.updateTGasConcRule(form);

        ArgumentCaptor<TGasConcRule> captor = ArgumentCaptor.forClass(TGasConcRule.class);
        verify(gasConcRuleMapper).updateById(captor.capture());
        TGasConcRule saved = captor.getValue();
        assertEquals("改名", saved.getRuleName());
        assertEquals(99, saved.getPriority());
        // 阈值保持旧值
        assertEquals(new BigDecimal("20"), saved.getTh1Max());
        assertEquals(new BigDecimal("50"), saved.getTh2Max());
        assertEquals(new BigDecimal("100"), saved.getTh3Max());
    }

    // ---------- 交棒排期翻转 ----------

    @Test
    void rolloverScheduled_flipsOnlyDueVersions() throws Exception {
        TGasConcRule due = rule(1, "A", 1, "10", "30", "80",
                "2025-01-01 00:00:00", "2026-01-01 00:00:00", 0, 10);
        TGasConcRule notDue = rule(2, "B", 1, "10", "30", "80",
                "2026-01-01 00:00:00", "2027-01-01 00:00:00", 0, 10);
        when(gasConcRuleMapper.selectList(any()))
                .thenReturn(new ArrayList<TGasConcRule>(Arrays.asList(due, notDue)));
        when(gasConcRuleMapper.updateById(any())).thenReturn(1);

        int rows = service.rolloverScheduled(at("2026-06-01 00:00:00"));
        assertEquals(1, rows);
        assertEquals(1, due.getStatus());
        assertEquals(0, notDue.getStatus());
    }

    // ---------- 停用 ----------

    @Test
    void retire_now_setsRetiredAndEffEnd() throws Exception {
        TGasConcRule db = rule(1, "C", 1, "10", "30", "80",
                "2025-01-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectById(1L)).thenReturn(db);
        when(gasConcRuleMapper.updateById(any())).thenReturn(1);

        assertEquals(1, service.retire(1L, null, "admin"));
        assertEquals(1, db.getStatus());
        assertTrue(db.getEffEnd() != null);
    }

    @Test
    void retire_futureEnd_beforeStart_rejected() throws Exception {
        TGasConcRule db = rule(1, "C", 1, "10", "30", "80",
                "2026-06-01 00:00:00", null, 0, 10);
        when(gasConcRuleMapper.selectById(1L)).thenReturn(db);
        assertThrows(IllegalArgumentException.class,
                () -> service.retire(1L, at("2026-01-01 00:00:00"), "admin"));
    }
}
