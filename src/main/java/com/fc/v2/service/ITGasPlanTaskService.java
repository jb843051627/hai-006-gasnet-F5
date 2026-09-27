package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasPlanTask;

/**
 * 巡检计划到期任务派单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGasPlanTaskService {

    /** 按主键回查条目 */
    TGasPlanTask selectTGasPlanTaskById(Long id);

    /** 按条件分页台账查询 */
    List<TGasPlanTask> selectTGasPlanTaskList(QueryWrapper<TGasPlanTask> queryWrapper);

    /** 该时刻可处理的条目（执行窗口内 + 到期 + 尚未处理） */
    List<TGasPlanTask> listDue(Date at);

    /** 执行一次周期扫描并自动派单，返回**成功条数**；单条失败跳过继续 */
    int runOnce(Date at);

    /**
     * 手动批量派单。
     *
     * @param ids        任务主键集合
     * @param targetType 接收对象 0班组 1巡检员
     * @param targetId   接收对象主键；为 null 时按班组负载自动均衡分配
     * @param dispatchBy 派发人
     * @return 成功派发条数
     */
    int dispatchBatch(List<Long> ids, Integer targetType, Long targetId, String dispatchBy);

    /** 截至 at 的超期自然日数（未超期为 0） */
    int overdueDays(TGasPlanTask task, Date at);

    /**
     * 管段停用后取消其在派任务：该管段全部"待派"条目置为 3已取消（管段停用），
     * 后续周期扫描与手动派单均不再生成该管段的巡检派单。
     *
     * @param siteId 管段底档主键 t_gas_pipe.id
     * @param reason 取消原因（写入备注）
     * @return 取消条数
     */
    int cancelPendingBySite(Long siteId, String reason);
}
