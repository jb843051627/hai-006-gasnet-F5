package com.fc.v2.controller.admin;

import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasLeakCheck;
import com.fc.v2.service.ITGasLeakCheckService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 泄漏浓度检测评定记录 Controller（检测录入评定 + 时间段查询 + 复核重算）
 *
 * @author fuce
 * @date 2026-09-26
 */
@Api(value = "泄漏浓度检测评定记录")
@Controller
@RequestMapping("/gasLeakCheck")
public class GasLeakCheckController extends BaseController {

    private final String prefix = "admin/gasLeakCheck";

    @Autowired
    private ITGasLeakCheckService gasLeakCheckService;

    @ApiOperation(value = "评定记录台账跳转", notes = "评定记录台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasLeakCheck:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "泄漏浓度评定记录查询", action = "list")
    @ApiOperation(value = "评定记录台账", notes = "按检测时间段/检测点/等级/导入批次号查询历史评定")
    @GetMapping("/list")
    @RequiresPermissions("gasLeakCheck:list")
    @ResponseBody
    public ResultTable list(TGasLeakCheck record,
                            @RequestParam(value = "beginTime", required = false) Date beginTime,
                            @RequestParam(value = "endTime", required = false) Date endTime) {
        QueryWrapper<TGasLeakCheck> queryWrapper = new QueryWrapper<TGasLeakCheck>();
        queryWrapper.eq("del_flag", 0).eq("status", 0);
        if (record.getCheckNo() != null && !record.getCheckNo().isEmpty()) {
            queryWrapper.like("check_no", record.getCheckNo());
        }
        if (record.getBatchNo() != null && !record.getBatchNo().isEmpty()) {
            queryWrapper.eq("batch_no", record.getBatchNo());
        }
        if (record.getSiteNo() != null && !record.getSiteNo().isEmpty()) {
            queryWrapper.like("site_no", record.getSiteNo());
        }
        if (record.getSiteName() != null && !record.getSiteName().isEmpty()) {
            queryWrapper.like("site_name", record.getSiteName());
        }
        if (record.getGradeLevel() != null) {
            queryWrapper.eq("grade_level", record.getGradeLevel());
        }
        if (record.getRecheckStatus() != null) {
            queryWrapper.eq("recheck_status", record.getRecheckStatus());
        }
        if (beginTime != null) {
            // 时段绑定走墙钟字符串，规避 JDBC serverTimezone 偏移
            queryWrapper.ge("detect_time",
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(beginTime));
        }
        if (endTime != null) {
            queryWrapper.le("detect_time",
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(endTime));
        }
        queryWrapper.orderByDesc("detect_time");
        startPage();
        com.github.pagehelper.PageInfo<TGasLeakCheck> page =
                new com.github.pagehelper.PageInfo<TGasLeakCheck>(gasLeakCheckService.selectTGasLeakCheckList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "检测录入跳转", notes = "检测录入评定跳转")
    @GetMapping("/add")
    @RequiresPermissions("gasLeakCheck:add")
    public String add(ModelMap model) {
        return prefix + "/add";
    }

    @Log(title = "泄漏浓度检测录入评定", action = "add")
    @ApiOperation(value = "检测录入并评定", notes = "按检测时间定位当时在用规则版本判档，记录原始浓度与版本快照")
    @PostMapping("/add")
    @RequiresPermissions("gasLeakCheck:add")
    @ResponseBody
    public AjaxResult add(TGasLeakCheck record, String ruleCode) {
        try {
            TGasLeakCheck saved = gasLeakCheckService.evaluateAndSave(record, ruleCode);
            if (saved.getGradeLevel() == 0) {
                return success(200, "记录已保存，但检测时刻无在用规则版本，等级记为无法判定，请复核", saved);
            }
            return success(200, "评定完成：等级 " + gradeName(saved.getGradeLevel()), saved);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "泄漏浓度评定单条复核", action = "recheck")
    @ApiOperation(value = "单条复核重算", notes = "按原始检测时间重新选版判档，原评定不覆盖")
    @PostMapping("/recheck")
    @RequiresPermissions("gasLeakCheck:recheck")
    @ResponseBody
    public AjaxResult recheck(Long id, String recheckRemark) {
        TGasLeakCheck r = gasLeakCheckService.recheck(id, ShiroUtils.getLoginName(), recheckRemark);
        if (r == null) {
            return error("评定记录不存在");
        }
        String msg = r.getRecheckStatus() == 2
                ? "复核完成：等级由「" + gradeName(r.getGradeLevel()) + "」变更为「" + gradeName(r.getRecheckGradeLevel()) + "」"
                : "复核完成：等级维持「" + gradeName(r.getGradeLevel()) + "」";
        return success(200, msg, r);
    }

    @Log(title = "泄漏浓度评定批量复核", action = "recheckRange")
    @ApiOperation(value = "时间段批量复核重算", notes = "对检测时间落在时间段内的记录逐条按当时版本重算")
    @PostMapping("/recheckRange")
    @RequiresPermissions("gasLeakCheck:recheck")
    @ResponseBody
    public AjaxResult recheckRange(Date beginTime, Date endTime, String recheckRemark) {
        try {
            int ok = gasLeakCheckService.recheckRange(beginTime, endTime,
                    ShiroUtils.getLoginName(), recheckRemark);
            return ok > 0 ? success("批量复核完成，共重算 " + ok + " 条记录")
                    : error("该时间段内没有可重算的记录");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    /** 等级名（轻微/中度/严重/极严重/无法判定） */
    private static String gradeName(Integer grade) {
        if (grade == null) {
            return "未知";
        }
        switch (grade) {
            case 1:
                return "轻微泄漏";
            case 2:
                return "中度泄漏";
            case 3:
                return "严重泄漏";
            case 4:
                return "极严重泄漏";
            default:
                return "无法判定";
        }
    }
}
