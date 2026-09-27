package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TGasPipeArch;

import java.util.List;

/**
 * 管道/调压站档案卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITGasPipeArchService {

    /** 按主键查询 */
    TGasPipeArch selectTGasPipeArchById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TGasPipeArch> selectTGasPipeArchList(Wrapper<TGasPipeArch> queryWrapper);

    /** 新增 */
    int insertTGasPipeArch(TGasPipeArch record);

    /** 修改 */
    int updateTGasPipeArch(TGasPipeArch record);

    /** 批量删除 */
    int deleteTGasPipeArchByIds(String ids);

    /** 按主键删除 */
    int deleteTGasPipeArchById(Long id);
}
