package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.service.ITGasRepairOrderService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 抢修工单 Controller（state-machine 形状：五段处置流转入口）
 *
 * 处置链：接单 → 开始抢修 → 提交抢修结果 → 现场复核 → 完工封卡，
 * 仅允许逐段推进不得跳级；完工封卡后永久封存，不得再修改或删除。
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
    public ResultTable list(TGasRepairOrder record,
                            @RequestParam(value = "overdueOnly", required = false) Integer overdueOnly) {
        QueryWrapper<TGasRepairOrder> queryWrapper = new QueryWrapper<TGasRepairOrder>();
        queryWrapper.eq("del_flag", 0);
        if (record.getBizNo() != null && !record.getBizNo().isEmpty()) {
            queryWrapper.like("biz_no", record.getBizNo());
        }
        if (record.getSiteName() != null && !record.getSiteName().isEmpty()) {
            queryWrapper.like("site_name", record.getSiteName());
        }
        if (record.getStage() != null) {
            queryWrapper.eq("stage", record.getStage());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        if (overdueOnly != null && overdueOnly == 1) {
            queryWrapper.ne("status", 2).lt("due_at", new java.util.Date());
        }
        queryWrapper.orderByDesc("id");
        startPage();
        com.github.pagehelper.PageInfo<TGasRepairOrder> page =
                new com.github.pagehelper.PageInfo<TGasRepairOrder>(
                        gasRepairOrderService.selectTGasRepairOrderList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "建单跳转", notes = "燃气泄漏/应急建单跳转")
    @GetMapping("/addView")
    @RequiresPermissions("gasRepairOrder:create")
    public String addView(ModelMap model) {
        model.put("owners", gasRepairOrderService.listCandidateOwners());
        return prefix + "/add";
    }

    @Log(title = "抢修工单创建", action = "create")
    @ApiOperation(value = "创建抢修工单", notes = "燃气泄漏/应急接警建单，进入待接单环节")
    @PostMapping("/create")
    @RequiresPermissions("gasRepairOrder:create")
    @ResponseBody
    public AjaxResult create(TGasRepairOrder order) {
        TGasRepairOrder saved = gasRepairOrderService.create(order, ShiroUtils.getLoginName());
        return success("建单成功，工单号 " + saved.getBizNo() + "，已进入待接单");
    }

    @Log(title = "抢修工单接单", action = "accept")
    @ApiOperation(value = "接单", notes = "巡检员领取工单，待接单 → 已接单")
    @PostMapping("/accept")
    @RequiresPermissions("gasRepairOrder:accept")
    @ResponseBody
    public AjaxResult accept(Long id, String remark) {
        gasRepairOrderService.accept(id, ShiroUtils.getLoginName(), remark);
        return success("接单成功，请尽快开始抢修");
    }

    @Log(title = "抢修工单开始抢修", action = "startRepair")
    @ApiOperation(value = "开始抢修", notes = "已接单 → 抢修中，未接单不得开工")
    @PostMapping("/startRepair")
    @RequiresPermissions("gasRepairOrder:repair")
    @ResponseBody
    public AjaxResult startRepair(Long id, String remark) {
        gasRepairOrderService.startRepair(id, ShiroUtils.getLoginName(), remark);
        return success("已开始抢修");
    }

    @Log(title = "抢修结果提交", action = "submitResult")
    @ApiOperation(value = "提交抢修结果", notes = "抢修中 → 待复核，须填写抢修结果")
    @PostMapping("/submitResult")
    @RequiresPermissions("gasRepairOrder:repair")
    @ResponseBody
    public AjaxResult submitResult(Long id, String repairResult, String remark) {
        gasRepairOrderService.submitResult(id, ShiroUtils.getLoginName(), repairResult, remark);
        return success("抢修结果已提交，等待现场复核");
    }

    @Log(title = "抢修工单现场复核", action = "review")
    @ApiOperation(value = "现场复核", notes = "待复核 → 复核通过；不通过退回抢修中返工")
    @PostMapping("/review")
    @RequiresPermissions("gasRepairOrder:review")
    @ResponseBody
    public AjaxResult review(Long id, Boolean passed, String reviewRemark) {
        boolean ok = passed != null && passed;
        gasRepairOrderService.review(id, ShiroUtils.getLoginName(), ok, reviewRemark);
        return ok ? success("复核通过，可完工封卡") : success("复核不通过，已退回抢修环节返工");
    }

    @Log(title = "抢修工单完工封卡", action = "seal")
    @ApiOperation(value = "完工封卡", notes = "复核通过 → 已封卡，封卡后永久封存不得再改")
    @PostMapping("/seal")
    @RequiresPermissions("gasRepairOrder:seal")
    @ResponseBody
    public AjaxResult seal(Long id, String remark) {
        gasRepairOrderService.seal(id, ShiroUtils.getLoginName(), remark);
        return success("已完工封卡，工单永久封存");
    }

    @Log(title = "抢修工单记事修改", action = "editContent")
    @ApiOperation(value = "修改记事", notes = "仅未封卡工单可改记事，已封卡拒绝修改")
    @PostMapping("/editContent")
    @RequiresPermissions("gasRepairOrder:edit")
    @ResponseBody
    public AjaxResult editContent(Long id, String content) {
        return gasRepairOrderService.updateContent(id, content, ShiroUtils.getLoginName())
                ? success("记事已更新") : error("修改失败：工单已封卡");
    }

    @Log(title = "抢修工单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "软删；已完工封卡的工单永久封存不得删除")
    @PostMapping("/remove")
    @RequiresPermissions("gasRepairOrder:remove")
    @ResponseBody
    public AjaxResult remove(Long id) {
        return gasRepairOrderService.remove(id, ShiroUtils.getLoginName())
                ? success("删除成功") : error("删除失败：工单已封卡，永久封存不得删除");
    }

    @ApiOperation(value = "处置履历跳转", notes = "处置履历跳转")
    @GetMapping("/recordView")
    @RequiresPermissions("gasRepairOrder:list")
    public String recordView(Long orderId, ModelMap model) {
        model.put("orderId", orderId);
        return prefix + "/record";
    }

    @Log(title = "抢修工单处置履历查询", action = "records")
    @ApiOperation(value = "处置履历", notes = "按操作时间升序返回该单全部处置留痕")
    @GetMapping("/records")
    @RequiresPermissions("gasRepairOrder:list")
    @ResponseBody
    public ResultTable records(Long orderId) {
        return dataTable(gasRepairOrderService.listRecords(orderId));
    }
}
