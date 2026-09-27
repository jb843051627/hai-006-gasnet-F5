package com.fc.v2.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasImpRow;
import com.fc.v2.model.custom.GasLeakImportResult;

/**
 * 检漏数据导入核对行 Service接口（batch-process 形状：整批提交，无单条增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGasImpRowService {

    /** 按主键回查单行 */
    TGasImpRow selectTGasImpRowById(Long id);

    /** 核对行条件查询（行明细台账） */
    List<TGasImpRow> selectTGasImpRowList(QueryWrapper<TGasImpRow> queryWrapper);

    /** 批次台账：按批次聚合 总数/入库/退回/重复/最近导入时间（batchNo 为空查全部） */
    List<Map<String, Object>> listBatchSummary(String batchNo);

    /**
     * 整批导入：解析 Excel → 逐行校验（管段存在性/时间格式/浓度数字/经纬度范围）→
     * 合格行写入正式检测报告（按检测时刻在用规则版本判档），失败行记录错误原因。
     * 同一批次号重复上传时，与本批已入库记录业务键（管段+检测时间+浓度+经纬度）相同的行自动去重跳过。
     * 整批任一环节异常时回滚，不产生半截数据。
     *
     * @param batchNo  批次号（空则系统生成；修正重传须沿用原批次号才能去重）
     * @param in       Excel 文件流
     * @param operator 操作人登录名
     * @return 导入汇总（批次号/总数/入库/退回/重复）
     * @throws IllegalArgumentException 文件无法解析、缺表头、无数据行、超单批上限
     */
    GasLeakImportResult importExcel(String batchNo, InputStream in, String operator);

    /** 该批次的失败行明细（按行号升序，用于错误报告导出） */
    List<TGasImpRow> listErrors(String batchNo);
}
