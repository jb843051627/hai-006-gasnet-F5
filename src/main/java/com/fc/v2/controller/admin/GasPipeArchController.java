package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasPipeArch;
import com.fc.v2.service.ITGasPipeArchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 管道/调压站档案卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "管道/调压站档案卡")
@Controller
@RequestMapping("/GasPipeArchController")
public class GasPipeArchController extends BaseController {

    private final String prefix = "admin/gasPipeArch";

    @Autowired
    private ITGasPipeArchService gasPipeArchService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasnet:gasPipeArch:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "管道/调压站档案卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("gasnet:gasPipeArch:list")
    @ResponseBody
    public ResultTable list(TGasPipeArch record) {
        QueryWrapper<TGasPipeArch> queryWrapper = new QueryWrapper<TGasPipeArch>();
        startPage();
        com.github.pagehelper.PageInfo<TGasPipeArch> page =
                new com.github.pagehelper.PageInfo<TGasPipeArch>(gasPipeArchService.selectTGasPipeArchList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "管道/调压站档案卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("gasnet:gasPipeArch:add")
    @ResponseBody
    public AjaxResult add(TGasPipeArch record) {
        return toAjax(gasPipeArchService.insertTGasPipeArch(record));
    }

    @Log(title = "管道/调压站档案卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("gasnet:gasPipeArch:edit")
    @ResponseBody
    public AjaxResult editSave(TGasPipeArch record) {
        return toAjax(gasPipeArchService.updateTGasPipeArch(record));
    }

    @Log(title = "管道/调压站档案卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("gasnet:gasPipeArch:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(gasPipeArchService.deleteTGasPipeArchByIds(ids));
    }
}
