package com.fc.v2.controller.admin;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasImpRow;
import com.fc.v2.model.custom.GasLeakImportResult;
import com.fc.v2.service.ITGasImpRowService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.GasLeakExcelUtil;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 检漏数据批量导入 Controller（Excel 上传校验 + 错误报告 + 批次台账）
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "检漏数据批量导入")
@Controller
@RequestMapping("/gasLeakImport")
public class GasLeakImportController extends BaseController {

    private final String prefix = "admin/gasLeakImport";

    @Autowired
    private ITGasImpRowService gasImpRowService;

    @ApiOperation(value = "导入台账跳转", notes = "导入台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasLeakImport:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "检漏数据批量导入", action = "upload")
    @ApiOperation(value = "Excel批量上传", notes = "逐行校验：管段编号存在性/检测时间格式/浓度数字/经纬度范围；同批次重复上传自动去重；合格行自动入库形成正式检测报告")
    @PostMapping("/upload")
    @RequiresPermissions("gasLeakImport:upload")
    @ResponseBody
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "batchNo", required = false) String batchNo) {
        if (file == null || file.isEmpty()) {
            return error("请选择要上传的检漏数据Excel文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"))) {
            return error("仅支持 .xlsx/.xls 格式的Excel文件");
        }
        try (InputStream in = file.getInputStream()) {
            GasLeakImportResult result = gasImpRowService.importExcel(batchNo, in, ShiroUtils.getLoginName());
            String msg = "导入完成：共 " + result.getTotalRows() + " 行，入库 " + result.getOkRows()
                    + " 行，退回 " + result.getFailRows() + " 行，重复跳过 " + result.getDupRows() + " 行";
            if (result.getFailRows() > 0) {
                msg += "。请下载错误报告修正后，沿用批次号「" + result.getBatchNo() + "」重新上传";
            }
            return success(200, msg, result);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error("导入失败：" + e.getMessage());
        }
    }

    @ApiOperation(value = "批次台账", notes = "按批次聚合：总数/入库/退回/重复/最近导入时间")
    @GetMapping("/batchList")
    @RequiresPermissions("gasLeakImport:list")
    @ResponseBody
    public ResultTable batchList(@RequestParam(value = "batchNo", required = false) String batchNo) {
        startPage();
        PageInfo<Map<String, Object>> page = new PageInfo<Map<String, Object>>(gasImpRowService.listBatchSummary(batchNo));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "核对行明细", notes = "按批次号/落地情形/管段编号查询导入核对行")
    @GetMapping("/list")
    @RequiresPermissions("gasLeakImport:list")
    @ResponseBody
    public ResultTable list(TGasImpRow record) {
        QueryWrapper<TGasImpRow> queryWrapper = new QueryWrapper<TGasImpRow>();
        queryWrapper.eq("del_flag", 0);
        if (record.getBatchNo() != null && !record.getBatchNo().isEmpty()) {
            queryWrapper.eq("batch_no", record.getBatchNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        if (record.getSiteNo() != null && !record.getSiteNo().isEmpty()) {
            queryWrapper.like("site_no", record.getSiteNo());
        }
        queryWrapper.orderByDesc("create_time").orderByAsc("row_no");
        startPage();
        PageInfo<TGasImpRow> page = new PageInfo<TGasImpRow>(gasImpRowService.selectTGasImpRowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "检漏导入错误报告导出", action = "export")
    @ApiOperation(value = "错误报告下载", notes = "导出该批次全部校验失败行（含原始值与错误原因），修正后可按原批次号重传")
    @GetMapping("/errorReport")
    @RequiresPermissions("gasLeakImport:export")
    public void errorReport(@RequestParam("batchNo") String batchNo, HttpServletResponse response) throws Exception {
        List<TGasImpRow> errors = gasImpRowService.listErrors(batchNo);
        setExcelResponse(response, "检漏导入错误报告_" + batchNo + ".xlsx");
        GasLeakExcelUtil.writeErrorReport(errors, response.getOutputStream());
    }

    @ApiOperation(value = "导入模板下载", notes = "标准表头：管段编号/检测时间/浓度值(ppm)/经度/纬度/设备编号(选填)/备注(选填)")
    @GetMapping("/template")
    @RequiresPermissions("gasLeakImport:view")
    public void template(HttpServletResponse response) throws Exception {
        setExcelResponse(response, "检漏数据导入模板.xlsx");
        GasLeakExcelUtil.writeTemplate(response.getOutputStream());
    }

    /** 统一设置 xlsx 下载响应头（文件名走 filename* 兼容中文） */
    private static void setExcelResponse(HttpServletResponse response, String filename) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + URLEncoder.encode(filename, "UTF-8"));
    }
}
