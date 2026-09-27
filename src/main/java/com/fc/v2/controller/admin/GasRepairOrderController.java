package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.service.ITGasRepairOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 抢修工单 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "抢修工单")
@Controller
@RequestMapping("/gasRepairOrder")
public class GasRepairOrderController extends BaseController {

    private final String prefix = "admin/gasRepairOrder";

    @Autowired
    private ITGasRepairOrderService gasRepairOrderService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasRepairOrder:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "抢修工单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("gasRepairOrder:list")
    @ResponseBody
    public ResultTable list(TGasRepairOrder record) {
        QueryWrapper<TGasRepairOrder> queryWrapper = new QueryWrapper<TGasRepairOrder>();
        startPage();
        com.github.pagehelper.PageInfo<TGasRepairOrder> page =
                new com.github.pagehelper.PageInfo<TGasRepairOrder>(gasRepairOrderService.selectTGasRepairOrderList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "抢修工单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("gasRepairOrder:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(gasRepairOrderService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "抢修工单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("gasRepairOrder:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(gasRepairOrderService.rollback(id, remark) != null ? 1 : 0);
    }
}
