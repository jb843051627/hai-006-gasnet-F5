package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGasRepairOrderMapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.service.ITGasRepairOrderService;

/**
 * 抢修工单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGasRepairOrderServiceImpl implements ITGasRepairOrderService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TGasRepairOrderMapper gasRepairOrderMapper;

    @Override
    public TGasRepairOrder selectTGasRepairOrderById(Long id) {
        return this.gasRepairOrderMapper.selectById(id);
    }

    @Override
    public List<TGasRepairOrder> selectTGasRepairOrderList(QueryWrapper<TGasRepairOrder> queryWrapper) {
        return this.gasRepairOrderMapper.selectList(queryWrapper);
    }

    @Override
    public TGasRepairOrder advance(Long id, String remark) {
        TGasRepairOrder r = this.gasRepairOrderMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.gasRepairOrderMapper.updateById(r);
        return r;
    }

    @Override
    public TGasRepairOrder rollback(Long id, String remark) {
        TGasRepairOrder r = this.gasRepairOrderMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.gasRepairOrderMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TGasRepairOrder r = this.gasRepairOrderMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.gasRepairOrderMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TGasRepairOrder r = this.gasRepairOrderMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.gasRepairOrderMapper.deleteById(id) > 0;
    }

}
