package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasTaskDispatch;
import com.fc.v2.service.ITGasTaskDispatchService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 巡检任务派发记录 Controller
 *
 * @author fuce
 * @date 2026-09-26
 */
@Api(value = "巡检任务派发记录")
@Controller
@RequestMapping("/gasTaskDispatch")
public class GasTaskDispatchController extends BaseController {

    private final String prefix = "admin/gasTaskDispatch";

    @Autowired
    private ITGasTaskDispatchService gasTaskDispatchService;

    @ApiOperation(value = "派发记录跳转", notes = "派发记录跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasTaskDispatch:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "巡检任务派发记录查询", action = "list")
    @ApiOperation(value = "派发记录台账", notes = "派发记录台账")
    @GetMapping("/list")
    @RequiresPermissions("gasTaskDispatch:list")
    @ResponseBody
    public ResultTable list(TGasTaskDispatch record) {
        QueryWrapper<TGasTaskDispatch> queryWrapper = new QueryWrapper<TGasTaskDispatch>();
        queryWrapper.eq("del_flag", 0);
        if (record.getItemNo() != null && !record.getItemNo().isEmpty()) {
            queryWrapper.like("item_no", record.getItemNo());
        }
        if (record.getTargetName() != null && !record.getTargetName().isEmpty()) {
            queryWrapper.like("target_name", record.getTargetName());
        }
        if (record.getExecStatus() != null) {
            queryWrapper.eq("exec_status", record.getExecStatus());
        }
        if (record.getUrgent() != null) {
            queryWrapper.eq("urgent", record.getUrgent());
        }
        queryWrapper.orderByDesc("dispatch_time");
        startPage();
        com.github.pagehelper.PageInfo<TGasTaskDispatch> page =
                new com.github.pagehelper.PageInfo<TGasTaskDispatch>(gasTaskDispatchService.selectTGasTaskDispatchList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "派发记录执行推进", action = "updateExec")
    @ApiOperation(value = "更新执行情况", notes = "更新执行情况（0待执行 1执行中 2已完成）")
    @PostMapping("/updateExec")
    @RequiresPermissions("gasTaskDispatch:updateExec")
    @ResponseBody
    public AjaxResult updateExec(Long id, Integer execStatus) {
        return toAjax(gasTaskDispatchService.updateExecStatus(id, execStatus, ShiroUtils.getLoginName()));
    }
}
