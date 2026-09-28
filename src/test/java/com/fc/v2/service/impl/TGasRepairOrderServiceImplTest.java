package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.fc.v2.common.exception.StateFlowException;
import com.fc.v2.mapper.auto.TGasRepairOrderMapper;
import com.fc.v2.mapper.auto.TGasRepairOrderRecordMapper;
import com.fc.v2.mapper.auto.TSysNoticeMapper;
import com.fc.v2.mapper.auto.TSysNoticeUserMapper;
import com.fc.v2.mapper.auto.TSysUserMapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.model.auto.TGasRepairOrderRecord;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITSysEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 抢修工单五段处置状态机单元测试（不依赖 Spring 容器与数据库，mapper 全部打桩）。
 * 覆盖：创建 → 接单 → 开始抢修 → 提交结果 → 复核通过 → 完工封卡全链路；
 * 禁止跳级；复核不通过退回抢修返工；封卡后不可改删；超期预警扫描。
 */
@ExtendWith(MockitoExtension.class)
public class TGasRepairOrderServiceImplTest {

    @Mock
    private TGasRepairOrderMapper gasRepairOrderMapper;
    @Mock
    private TGasRepairOrderRecordMapper gasRepairOrderRecordMapper;
    @Mock
    private TSysUserMapper sysUserMapper;
    @Mock
    private TSysNoticeMapper sysNoticeMapper;
    @Mock
    private TSysNoticeUserMapper sysNoticeUserMapper;
    @Mock
    private ITSysEmailService sysEmailService;

    private TGasRepairOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TGasRepairOrderServiceImpl();
        ReflectionTestUtils.setField(service, "gasRepairOrderMapper", gasRepairOrderMapper);
        ReflectionTestUtils.setField(service, "gasRepairOrderRecordMapper", gasRepairOrderRecordMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "sysNoticeMapper", sysNoticeMapper);
        ReflectionTestUtils.setField(service, "sysNoticeUserMapper", sysNoticeUserMapper);
        ReflectionTestUtils.setField(service, "sysEmailService", sysEmailService);
    }

    private TGasRepairOrder orderAt(int stage, int status) {
        TGasRepairOrder o = new TGasRepairOrder();
        o.setId(6001L);
        o.setBizNo("WX0001");
        o.setSiteName("东湖小区3号楼");
        o.setContent("燃气泄漏");
        o.setStage(stage);
        o.setStatus(status);
        o.setDelFlag(0);
        o.setWarnCount(0);
        return o;
    }

    private TSysUser inspector() {
        TSysUser u = new TSysUser();
        u.setId(1L);
        u.setUsername("inspector_a");
        u.setNickname("巡检员甲");
        return u;
    }

    @Test
    void createRequiresSiteAndContent() {
        TGasRepairOrder empty = new TGasRepairOrder();
        assertThrows(StateFlowException.class, () -> service.create(empty, "ops"));

        TGasRepairOrder ok = new TGasRepairOrder();
        ok.setSiteName("现场");
        ok.setContent("泄漏");
        when(gasRepairOrderMapper.insert(any())).thenReturn(1);
        TGasRepairOrder saved = service.create(ok, "ops");
        assertEquals(0, saved.getStage());
        assertEquals(0, saved.getStatus());
        assertNotNull(saved.getBizNo());

        ArgumentCaptor<TGasRepairOrderRecord> recCap = ArgumentCaptor.forClass(TGasRepairOrderRecord.class);
        verify(gasRepairOrderRecordMapper).insert(recCap.capture());
        assertEquals(0, recCap.getValue().getAction());
        assertEquals("待接单", recCap.getValue().getStageName());
        assertEquals("ops", recCap.getValue().getOperator());
        assertNotNull(recCap.getValue().getCreateTime());
    }

    @Test
    void fullFlowWalksFiveStagesAndSealsForever() {
        TSysUser insp = inspector();
        when(sysUserMapper.selectOne(any())).thenReturn(insp);

        // 接单：0 → 1
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(0, 0));
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(1);
        TGasRepairOrder accepted = service.accept(6001L, "inspector_a", "接单");
        assertEquals(1, accepted.getStage());
        assertEquals(1, accepted.getStatus());
        assertEquals("inspector_a", accepted.getAcceptBy());
        assertNotNull(accepted.getAcceptTime());

        // 开始抢修：1 → 2
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(1, 1));
        TGasRepairOrder repairing = service.startRepair(6001L, "inspector_a", "开工");
        assertEquals(2, repairing.getStage());

        // 提交抢修结果：2 → 3（结果必填）
        assertThrows(StateFlowException.class,
                () -> service.submitResult(6001L, "inspector_a", "  ", null));
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(2, 1));
        TGasRepairOrder reviewing = service.submitResult(6001L, "inspector_a", "更换垫片，复测0ppm", null);
        assertEquals(3, reviewing.getStage());

        // 现场复核通过：3 → 4（意见必填）
        assertThrows(StateFlowException.class,
                () -> service.review(6001L, "leader", true, ""));
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(3, 1));
        TGasRepairOrder passed = service.review(6001L, "leader", true, "复测合格");
        assertEquals(4, passed.getStage());

        // 完工封卡：4 → 5(status=2)，永久封存
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(4, 1));
        TGasRepairOrder sealed = service.seal(6001L, "leader", null);
        assertEquals(5, sealed.getStage());
        assertEquals(2, sealed.getStatus());
        assertEquals("leader", sealed.getSealBy());
        assertNotNull(sealed.getSealTime());

        // 六个动作各落一条履历，操作人/时间/环节留痕
        verify(gasRepairOrderRecordMapper, times(5)).insert(any());
    }

    @Test
    void cannotSkipStages() {
        // 停在待接单(0)，直接"开始抢修"必须拒绝
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(0, 0));
        StateFlowException ex = assertThrows(StateFlowException.class,
                () -> service.startRepair(6001L, "inspector_a", null));
        assertTrue(ex.getMessage().contains("不允许跳级"));
        verify(gasRepairOrderMapper, never()).update(any(), any());
        verify(gasRepairOrderRecordMapper, never()).insert(any());

        // 停在已接单(1)，直接"完工封卡"必须拒绝
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(1, 1));
        assertThrows(StateFlowException.class, () -> service.seal(6001L, "boss", null));
    }

    @Test
    void reviewFailedGoesBackToRepairingThenResubmit() {
        // 3待复核 复核不通过 → 退回 2抢修中
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(3, 1));
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(1);
        TGasRepairOrder back = service.review(6001L, "leader", false, "仍有微漏，重新处理");
        assertEquals(2, back.getStage());
        assertEquals(1, back.getStatus());

        // 返工后重新提交结果：2 → 3
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(2, 1));
        TGasRepairOrder reSubmitted = service.submitResult(6001L, "inspector_a", "二次更换法兰，复测0ppm", null);
        assertEquals(3, reSubmitted.getStage());
    }

    @Test
    void sealedOrderRejectsEveryMutation() {
        // 已封卡(stage=5/status=2)：接单/开工/提交/复核/封卡/改记事/删除全部拒绝
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(5, 2));
        assertThrows(StateFlowException.class, () -> service.accept(6001L, "a", null));
        assertThrows(StateFlowException.class, () -> service.startRepair(6001L, "a", null));
        assertThrows(StateFlowException.class, () -> service.submitResult(6001L, "a", "结果", null));
        assertThrows(StateFlowException.class, () -> service.review(6001L, "a", true, "意见"));
        assertThrows(StateFlowException.class, () -> service.seal(6001L, "a", null));
        assertThrows(StateFlowException.class, () -> service.updateContent(6001L, "新记事", "a"));
        assertThrows(StateFlowException.class, () -> service.remove(6001L, "a"));
        verify(gasRepairOrderMapper, never()).update(any(), any());
        verify(gasRepairOrderRecordMapper, never()).insert(any());
    }

    @Test
    void removeIsSoftDeleteOnlyForOpenOrders() {
        when(gasRepairOrderMapper.selectById(6001L)).thenReturn(orderAt(1, 1));
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.remove(6001L, "admin"));
        ArgumentCaptor<TGasRepairOrder> cap = ArgumentCaptor.forClass(TGasRepairOrder.class);
        verify(gasRepairOrderMapper).update(cap.capture(), any());
        assertEquals(1, cap.getValue().getDelFlag());
    }

    @Test
    void scanOverdueWarnsOwnerOncePerInterval() {
        // 两张超期未封卡工单：6001 从未预警；6002 10小时前预警过（24h内不重复）
        TGasRepairOrder due = orderAt(2, 1);
        due.setOwnerId(1L);
        due.setDueAt(new Date(System.currentTimeMillis() - 2L * 24 * 3600 * 1000));
        TGasRepairOrder recentlyWarned = orderAt(1, 1);
        recentlyWarned.setId(6002L);
        recentlyWarned.setOwnerId(1L);
        recentlyWarned.setDueAt(new Date(System.currentTimeMillis() - 2L * 24 * 3600 * 1000));
        recentlyWarned.setLastWarnTime(new Date(System.currentTimeMillis() - 10L * 3600 * 1000));

        List<TGasRepairOrder> overdue = new ArrayList<TGasRepairOrder>(Arrays.asList(due, recentlyWarned));
        when(gasRepairOrderMapper.selectList(any())).thenReturn(overdue);
        when(sysUserMapper.selectById(1L)).thenReturn(inspector());
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(1);

        int warned = service.scanOverdue(new Date());
        assertEquals(1, warned);
        verify(sysNoticeMapper).insert(any());
        verify(sysNoticeUserMapper).insert(any());
    }
}
