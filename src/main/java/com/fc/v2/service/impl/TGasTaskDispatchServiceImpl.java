package com.fc.v2.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGasTaskDispatchMapper;
import com.fc.v2.model.auto.TGasTaskDispatch;
import com.fc.v2.service.ITGasTaskDispatchService;

/**
 * 巡检到期任务派发记录 Service业务层处理
 *
 * @author fuce
 * @date 2026-09-26
 */
@Service
public class TGasTaskDispatchServiceImpl implements ITGasTaskDispatchService {

    /** 记录情形：有效 */
    private static final int STATUS_VALID = 0;
    /** 执行情况：已完成 */
    private static final int EXEC_DONE = 2;

    @javax.annotation.Resource
    private TGasTaskDispatchMapper gasTaskDispatchMapper;

    @Override
    public TGasTaskDispatch selectTGasTaskDispatchById(Long id) {
        return this.gasTaskDispatchMapper.selectById(id);
    }

    @Override
    public List<TGasTaskDispatch> selectTGasTaskDispatchList(QueryWrapper<TGasTaskDispatch> queryWrapper) {
        return this.gasTaskDispatchMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTGasTaskDispatch(TGasTaskDispatch record) {
        return this.gasTaskDispatchMapper.insert(record);
    }

    @Override
    public int deleteTGasTaskDispatchById(Long id) {
        return this.gasTaskDispatchMapper.deleteById(id);
    }

    /**
     * 负载口径：有效记录 且 执行情况为 待执行/执行中（即 exec_status < 2）。
     * 数据量为班组级，逐条内存归并，避免数据库方言差异。
     */
    @Override
    public Map<String, Integer> countOpenLoadByTarget() {
        QueryWrapper<TGasTaskDispatch> qw = new QueryWrapper<TGasTaskDispatch>();
        qw.eq("del_flag", 0).eq("status", STATUS_VALID).lt("exec_status", EXEC_DONE);
        List<TGasTaskDispatch> open = this.gasTaskDispatchMapper.selectList(qw);
        Map<String, Integer> load = new HashMap<String, Integer>();
        for (TGasTaskDispatch r : open) {
            String key = r.getTargetType() + ":" + r.getTargetId();
            load.merge(key, 1, Integer::sum);
        }
        return load;
    }

    @Override
    public int updateExecStatus(Long id, Integer execStatus, String updateBy) {
        TGasTaskDispatch r = this.gasTaskDispatchMapper.selectById(id);
        if (r == null || execStatus == null || execStatus < 0 || execStatus > EXEC_DONE) {
            return 0;
        }
        r.setExecStatus(execStatus);
        r.setFinishTime(execStatus == EXEC_DONE ? new Date() : null);
        r.setUpdateBy(updateBy);
        r.setUpdateTime(new Date());
        return this.gasTaskDispatchMapper.updateById(r);
    }
}
