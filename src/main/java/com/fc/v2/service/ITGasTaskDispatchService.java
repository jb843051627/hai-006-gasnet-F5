package com.fc.v2.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasTaskDispatch;

/**
 * 巡检到期任务派发记录 Service接口
 *
 * @author fuce
 * @date 2026-09-26
 */
public interface ITGasTaskDispatchService {

    /** 按主键回查派发记录 */
    TGasTaskDispatch selectTGasTaskDispatchById(Long id);

    /** 按条件查询派发记录 */
    List<TGasTaskDispatch> selectTGasTaskDispatchList(QueryWrapper<TGasTaskDispatch> queryWrapper);

    /** 落一条派发记录 */
    int insertTGasTaskDispatch(TGasTaskDispatch record);

    /** 按主键删除派发记录（派单失败补偿用） */
    int deleteTGasTaskDispatchById(Long id);

    /**
     * 各接收对象当前负载（有效且未完成的派发条数）。
     * key = targetType + ":" + targetId
     */
    Map<String, Integer> countOpenLoadByTarget();

    /** 推进执行情况（0待执行 1执行中 2已完成），完成时落完成时间 */
    int updateExecStatus(Long id, Integer execStatus, String updateBy);
}
