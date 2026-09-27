package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.fc.v2.mapper.auto.TGasLeakCheckMapper;
import com.fc.v2.model.auto.TGasConcRule;
import com.fc.v2.model.auto.TGasLeakCheck;
import com.fc.v2.service.ITGasConcRuleService;

/**
 * 检测评定：按检测时刻选版入库、跨规则变更点的历史复核不漂移
 */
@ExtendWith(MockitoExtension.class)
class TGasLeakCheckServiceImplTest {

    @Mock
    private TGasLeakCheckMapper gasLeakCheckMapper;
    @Mock
    private ITGasConcRuleService gasConcRuleService;

    @InjectMocks
    private TGasLeakCheckServiceImpl service;

    private static Date at(String ts) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ts);
    }

    private static TGasConcRule rule(long id, String code, int version, String t1, String t2, String t3) {
        TGasConcRule r = new TGasConcRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setVersion(version);
        r.setTh1Max(new BigDecimal(t1));
        r.setTh2Max(new BigDecimal(t2));
        r.setTh3Max(new BigDecimal(t3));
        return r;
    }

    private static TGasLeakCheck record(String detectTime, String conc) throws Exception {
        TGasLeakCheck c = new TGasLeakCheck();
        c.setDetectTime(at(detectTime));
        c.setConcValue(new BigDecimal(conc));
        c.setSiteNo("GP00-V01");
        return c;
    }

    /**
     * gradeBy 打在 mock 接口上默认返回 0，这里按生产口径（等于上限取高一档）给出真实应答，
     * 使评定/复核结果可被验证；生产侧分档逻辑仍以 TGasConcRuleServiceImpl 单测为准。
     */
    private void stubGradeBy() {
        when(gasConcRuleService.gradeBy(any(), any())).thenAnswer(inv -> {
            TGasConcRule r = inv.getArgument(0);
            BigDecimal v = inv.getArgument(1);
            if (r == null || v == null) {
                return 0;
            }
            if (v.compareTo(r.getTh1Max()) < 0) {
                return 1;
            }
            if (v.compareTo(r.getTh2Max()) < 0) {
                return 2;
            }
            if (v.compareTo(r.getTh3Max()) < 0) {
                return 3;
            }
            return 4;
        });
    }

    @Test
    void evaluateAndSave_usesRuleEffectiveAtDetectTime_andSnapshotsVersion() throws Exception {
        TGasConcRule v1 = rule(8001, "CITY-MID", 1, "20", "50", "100");
        when(gasConcRuleService.effectiveAt(eq("CITY-MID"), any(Date.class))).thenReturn(v1);
        when(gasLeakCheckMapper.insert(any())).thenAnswer(inv -> {
            TGasLeakCheck c = inv.getArgument(0);
            c.setId(123456789L);
            return 1;
        });
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);
        stubGradeBy();

        TGasLeakCheck saved = service.evaluateAndSave(record("2025-12-15 10:00:00", "15"), "CITY-MID");

        assertEquals(1, saved.getGradeLevel());
        assertEquals(8001L, saved.getRuleId());
        assertEquals("CITY-MID", saved.getRuleCode());
        assertEquals(1, saved.getRuleVersion());
        assertEquals(0, saved.getRecheckStatus());
        assertEquals(0, saved.getStatus());
        // 记录号落定
        assertEquals(true, saved.getCheckNo().startsWith("LC20251215100000"));
    }

    @Test
    void evaluateAndSave_noRuleAtThatTime_gradeZeroButPersisted() throws Exception {
        when(gasConcRuleService.effectiveAt(any(), any(Date.class))).thenReturn(null);
        when(gasLeakCheckMapper.insert(any())).thenAnswer(inv -> {
            ((TGasLeakCheck) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);

        TGasLeakCheck saved = service.evaluateAndSave(record("2026-06-01 10:00:00", "15"), "CITY-MID");
        assertEquals(0, saved.getGradeLevel());
        assertNull(saved.getRuleId());
        assertNull(saved.getRuleVersion());
    }

    @Test
    void evaluateAndSave_blankRuleCode_fallsBackToTopPriority() throws Exception {
        TGasConcRule top = rule(9, "TOP", 3, "10", "30", "80");
        when(gasConcRuleService.listAvailable(any(Date.class))).thenReturn(Collections.singletonList(top));
        stubGradeBy();
        when(gasLeakCheckMapper.insert(any())).thenAnswer(inv -> {
            ((TGasLeakCheck) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);

        TGasLeakCheck saved = service.evaluateAndSave(record("2026-09-20 10:00:00", "50"), null);
        assertEquals(3, saved.getGradeLevel());
        assertEquals("TOP", saved.getRuleCode());
        verify(gasConcRuleService, never()).effectiveAt(any(), any());
    }

    @Test
    void evaluateAndSave_invalidArgs_rejected() throws Exception {
        TGasLeakCheck noTime = new TGasLeakCheck();
        noTime.setConcValue(new BigDecimal("1"));
        assertThrows(IllegalArgumentException.class, () -> service.evaluateAndSave(noTime, null));

        TGasLeakCheck negConc = record("2026-09-20 10:00:00", "-0.01");
        assertThrows(IllegalArgumentException.class, () -> service.evaluateAndSave(negConc, null));
    }

    @Test
    void recheck_usesOriginalDetectTime_soHistoricalGradeDoesNotDrift() throws Exception {
        // 原评定：2025-12 检测，当时 V1（阈值 20/50/100）→ 15ppm 轻微(1)
        TGasLeakCheck db = record("2025-12-15 10:00:00", "15");
        db.setId(8101L);
        db.setGradeLevel(1);
        db.setRuleId(8001L);
        db.setRuleCode("CITY-MID");
        db.setRuleVersion(1);
        db.setDelFlag(0);
        when(gasLeakCheckMapper.selectById(8101L)).thenReturn(db);
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);

        // 复核时（2026-09）仍须按原始检测时间选回 V1 —— mock 断言 effectiveAt 收到的是 2025-12-15
        TGasConcRule v1 = rule(8001, "CITY-MID", 1, "20", "50", "100");
        when(gasConcRuleService.effectiveAt(eq("CITY-MID"), eq(at("2025-12-15 10:00:00")))).thenReturn(v1);
        stubGradeBy();

        TGasLeakCheck re = service.recheck(8101L, "auditor", "例行复核");

        // 等级维持轻微(1)，原快照不被覆盖
        assertEquals(1, re.getRecheckGradeLevel());
        assertEquals(1, re.getRecheckStatus());
        assertEquals(1, re.getGradeLevel());
        assertEquals(1, re.getRuleVersion());
        assertEquals("auditor", re.getRecheckBy());
        verify(gasLeakCheckMapper).updateById(any());
    }

    @Test
    void recheck_changedGrade_markedAsChanged() throws Exception {
        // 原评定错按中度(2) 入库；复核按原检测时间重算应为轻微(1)
        TGasLeakCheck db = record("2025-12-15 10:00:00", "15");
        db.setId(8101L);
        db.setGradeLevel(2);
        db.setRuleCode("CITY-MID");
        db.setRuleVersion(1);
        db.setDelFlag(0);
        when(gasLeakCheckMapper.selectById(8101L)).thenReturn(db);
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);

        TGasConcRule v1 = rule(8001, "CITY-MID", 1, "20", "50", "100");
        when(gasConcRuleService.effectiveAt(eq("CITY-MID"), any(Date.class))).thenReturn(v1);
        stubGradeBy();

        TGasLeakCheck re = service.recheck(8101L, "auditor", null);
        assertEquals(1, re.getRecheckGradeLevel());
        assertEquals(2, re.getRecheckStatus()); // 重算后等级变更
    }

    @Test
    void recheck_missingRule_nowGradeZeroAndChanged() throws Exception {
        // 原代号当时已无在用版本（被删除/停用）→ 复核等级 0，不跨代号回退
        TGasLeakCheck db = record("2026-06-01 10:00:00", "15");
        db.setId(1L);
        db.setGradeLevel(1);
        db.setRuleCode("GONE");
        db.setRuleVersion(1);
        db.setDelFlag(0);
        when(gasLeakCheckMapper.selectById(1L)).thenReturn(db);
        when(gasConcRuleService.effectiveAt(eq("GONE"), any(Date.class))).thenReturn(null);
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);

        TGasLeakCheck re = service.recheck(1L, "auditor", null);
        assertEquals(0, re.getRecheckGradeLevel());
        assertNull(re.getRecheckRuleId());
        assertEquals(2, re.getRecheckStatus());
    }

    @Test
    void recheckRange_onlyRecomputesRecordsInTimeWindow() throws Exception {
        TGasLeakCheck a = record("2026-09-01 08:00:00", "15");
        a.setId(1L);
        a.setGradeLevel(2);
        a.setRuleCode("C");
        a.setRuleVersion(1);
        a.setStatus(0);
        a.setDelFlag(0);
        TGasLeakCheck b = record("2026-09-02 08:00:00", "60");
        b.setId(2L);
        b.setGradeLevel(3);
        b.setRuleCode("C");
        b.setRuleVersion(2);
        b.setStatus(0);
        b.setDelFlag(0);
        when(gasLeakCheckMapper.selectList(any())).thenReturn(new ArrayList<TGasLeakCheck>(Arrays.asList(a, b)));
        when(gasLeakCheckMapper.updateById(any())).thenReturn(1);

        TGasConcRule v = rule(8002, "C", 2, "10", "30", "80");
        when(gasConcRuleService.effectiveAt(eq("C"), any(Date.class))).thenReturn(v);
        stubGradeBy();

        int ok = service.recheckRange(at("2026-09-01 00:00:00"), at("2026-09-30 23:59:59"), "auditor", "批量");
        assertEquals(2, ok);
        // 15→中度(2)维持；60→严重(3)维持
        assertEquals(1, a.getRecheckStatus());
        assertEquals(1, b.getRecheckStatus());
        assertEquals("auditor", a.getRecheckBy());
    }

    @Test
    void recheckRange_illegalWindow_rejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.recheckRange(at("2026-09-30 00:00:00"), at("2026-09-01 00:00:00"), "a", null));
    }

    @Test
    void recheck_deletedRecord_returnsNull() {
        when(gasLeakCheckMapper.selectById(404L)).thenReturn(null);
        assertNull(service.recheck(404L, "a", null));
        verify(gasLeakCheckMapper, never()).updateById(any());
    }
}
