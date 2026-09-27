package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasPlanTask;
import com.fc.v2.model.auto.TSysDepartment;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITGasPlanTaskService;
import com.fc.v2.service.ITSysDepartmentService;
import com.fc.v2.service.ITSysUserService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 巡检到期任务派单 Controller（scheduling-job 形状：扫描 + 批量派单）
 *
 * @author fuce
 * @date 2026-09-26
 */
@Api(value = "巡检到期任务派单")
@Controller
@RequestMapping("/gasPlanTask")
public class GasPlanTaskController extends BaseController {

    private final String prefix = "admin/gasPlanTask";

    @Autowired
    private ITGasPlanTaskService gasPlanTaskService;

    @Autowired
    private ITSysDepartmentService sysDepartmentService;

    @Autowired
    private ITSysUserService sysUserService;

    @ApiOperation(value = "到期任务台账跳转", notes = "到期任务台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasPlanTask:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "巡检到期任务台账查询", action = "list")
    @ApiOperation(value = "到期任务台账", notes = "到期任务台账")
    @GetMapping("/list")
    @RequiresPermissions("gasPlanTask:list")
    @ResponseBody
    public ResultTable list(TGasPlanTask record) {
        QueryWrapper<TGasPlanTask> queryWrapper = new QueryWrapper<TGasPlanTask>();
        queryWrapper.eq("del_flag", 0);
        if (record.getItemNo() != null && !record.getItemNo().isEmpty()) {
            queryWrapper.like("item_no", record.getItemNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        queryWrapper.orderByAsc("due_at");
        startPage();
        com.github.pagehelper.PageInfo<TGasPlanTask> page =
                new com.github.pagehelper.PageInfo<TGasPlanTask>(gasPlanTaskService.selectTGasPlanTaskList(queryWrapper));
        Date now = new Date();
        for (TGasPlanTask task : page.getList()) {
            int overdue = gasPlanTaskService.overdueDays(task, now);
            task.setOverdueDays(overdue);
            task.setUrgent(overdue >= 3 ? 1 : 0);
        }
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "到期任务手动扫描", action = "scan")
    @ApiOperation(value = "立即扫描一轮", notes = "立即扫描一轮并自动派单")
    @PostMapping("/scan")
    @RequiresPermissions("gasPlanTask:scan")
    @ResponseBody
    public AjaxResult scan() {
        int ok = gasPlanTaskService.runOnce(new Date());
        return success("扫描完成，本轮成功派单 " + ok + " 条");
    }

    @ApiOperation(value = "批量派单跳转", notes = "批量派单跳转")
    @GetMapping("/dispatchView")
    @RequiresPermissions("gasPlanTask:dispatch")
    public String dispatchView(String ids, ModelMap model) {
        QueryWrapper<TSysDepartment> teamQw = new QueryWrapper<TSysDepartment>();
        teamQw.eq("status", 1).orderByAsc("id");
        model.put("teams", sysDepartmentService.selectTSysDepartmentList(teamQw));
        model.put("inspectors", sysUserService.selectTSysUserList(new QueryWrapper<TSysUser>()));
        model.put("ids", ids);
        return prefix + "/dispatch";
    }

    @Log(title = "到期任务批量派单", action = "dispatch")
    @ApiOperation(value = "批量派单", notes = "指定班组/巡检员派发，或不指定时按班组负载自动均衡")
    @PostMapping("/dispatch")
    @RequiresPermissions("gasPlanTask:dispatch")
    @ResponseBody
    public AjaxResult dispatch(String ids, Integer targetType, Long targetId) {
        List<Long> idList = new ArrayList<Long>();
        if (ids != null) {
            for (String s : ids.split(",")) {
                if (!s.trim().isEmpty()) {
                    idList.add(Long.valueOf(s.trim()));
                }
            }
        }
        int ok = gasPlanTaskService.dispatchBatch(idList, targetType, targetId, ShiroUtils.getLoginName());
        return ok > 0 ? success("成功派发 " + ok + " 条任务") : error("没有可派发的任务（任务不存在、已派发或无可用班组）");
    }
}
