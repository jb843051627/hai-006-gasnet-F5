package com.fc.v2.gasnet;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGasRepairOrderMapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.service.ITGasRepairOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gasnet F5 抢修工单逐级推进与完工封存 · 功能点验收测试（state-machine 形状：直调流转服务，用例自播种）
 * create 侧产物，仅质检使用，不交付执行模型。
 */
@SpringBootTest
public class GasnetF5AcceptanceTest {

    /** 本测试类自己插入的单据编号前缀（@BeforeEach 只清这一批，不动其它数据） */
    private static final String PFX = "SM-";

    @javax.annotation.Resource
    private ITGasRepairOrderService gasRepairOrderService;

    @javax.annotation.Resource
    private TGasRepairOrderMapper gasRepairOrderMapper;

    @BeforeEach
    public void setUp() {
        gasRepairOrderMapper.delete(new QueryWrapper<TGasRepairOrder>().likeRight("biz_no", PFX));
    }

    private TGasRepairOrder mk(String no, int status, int stage) {
        TGasRepairOrder r = new TGasRepairOrder();
        r.setBizNo(no);
        r.setStatus(status);
        r.setStage(stage);
        r.setContent("初始内容");
        r.setLastAction(null);
        return r;
    }

    /** 坑1 单步推进 */
    @Test
    public void trap1_advance_should_move_one_stage() {
        TGasRepairOrder r = mk(PFX + "S1", 0, 0);
        gasRepairOrderMapper.insert(r);
        TGasRepairOrder after = gasRepairOrderService.advance(r.getId(), "提交");
        assertNotNull(after, "流转被拒");
        assertEquals(1, after.getStage().intValue(), "环节数不符合预期");
    }

    /** 坑5 已办结仍可删除 */
    @Test
    public void trap5_terminal_should_not_be_removed() {
        TGasRepairOrder r = mk(PFX + "S5", 2, 3);
        gasRepairOrderMapper.insert(r);
        assertFalse(gasRepairOrderService.remove(r.getId()), "已办结单据仍能删除");
        assertNotNull(gasRepairOrderService.selectTGasRepairOrderById(r.getId()), "单据被删掉了");
    }

    /** 坑2 连续重复发起 */
    @Test
    public void trap2_same_action_should_not_repeat() {
        TGasRepairOrder r = mk(PFX + "S2", 0, 0);
        gasRepairOrderMapper.insert(r);
        gasRepairOrderService.advance(r.getId(), "提交");
        TGasRepairOrder again = gasRepairOrderService.advance(r.getId(), "提交");
        assertNull(again, "同一动作连续发起未被拒绝");
    }

    /** 坑3 已办结仍可流转 */
    @Test
    public void trap3_terminal_should_not_advance() {
        TGasRepairOrder r = mk(PFX + "S3", 2, 3);
        gasRepairOrderMapper.insert(r);
        assertNull(gasRepairOrderService.advance(r.getId(), "再推一次"), "已办结单据仍能流转");
    }

    /** 坑6 回退档位与终态 */
    @Test
    public void trap6_rollback_should_move_back_one_stage_only() {
        TGasRepairOrder a = mk(PFX + "S6A", 1, 2);
        gasRepairOrderMapper.insert(a);
        TGasRepairOrder back = gasRepairOrderService.rollback(a.getId(), "退回");
        assertNotNull(back, "回退被拒");
        assertEquals(1, back.getStage().intValue(), "回退档数不符合预期");

        TGasRepairOrder b = mk(PFX + "S6B", 2, 3);
        gasRepairOrderMapper.insert(b);
        assertNull(gasRepairOrderService.rollback(b.getId(), "退回"), "已办结单据仍能回退");
    }

    /** 坑4 已办结仍可改内容 */
    @Test
    public void trap4_terminal_should_not_be_edited() {
        TGasRepairOrder r = mk(PFX + "S4", 2, 3);
        r.setContent("原始内容");
        gasRepairOrderMapper.insert(r);
        assertFalse(gasRepairOrderService.updateContent(r.getId(), "改成别的"), "已办结单据仍能改内容");
        assertEquals("原始内容", gasRepairOrderService.selectTGasRepairOrderById(r.getId()).getContent(), "内容被改动");
    }
}
