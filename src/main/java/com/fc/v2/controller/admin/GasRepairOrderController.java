package com.fc.v2.controller.admin;

import java.util.Date;

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
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.*;

/**
 * 抢修工单 Controller（state-machine 形状：五段顺序流转入口）
 *
 * 接单 → 开始抢修 → 提交抢修结果 → 现场复核 → 完工封卡，
 * 严格顺序流转不允许跳级；完工封卡后永久封存。
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

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Date.class, new CustomDateEditor(
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), true));
    }

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
        queryWrapper.eq("del_flag", 0);
        if (record.getBizNo() != null && !record.getBizNo().isEmpty()) {
            queryWrapper.like("biz_no", record.getBizNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        if (record.getStage() != null) {
            queryWrapper.eq("stage", record.getStage());
        }
        queryWrapper.orderByDesc("id");
        startPage();
        com.github.pagehelper.PageInfo<TGasRepairOrder> page =
                new com.github.pagehelper.PageInfo<TGasRepairOrder>(gasRepairOrderService.selectTGasRepairOrderList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "新建抢修工单跳转", notes = "发生燃气泄漏等险情时建单跳转")
    @GetMapping("/addView")
    @RequiresPermissions("gasRepairOrder:add")
    public String addView(ModelMap model) {
        model.put("owners", gasRepairOrderService.listOwners());
        return prefix + "/add";
    }

    @Log(title = "抢修工单建单", action = "add")
    @ApiOperation(value = "新建抢修工单", notes = "发生燃气泄漏等紧急情况时创建抢修工单，建单后处于待接单")
    @PostMapping("/add")
    @RequiresPermissions("gasRepairOrder:add")
    @ResponseBody
    public AjaxResult add(String content, Long ownerId,
                          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dueAt, String remark) {
        Long id = gasRepairOrderService.createOrder(content, ownerId, dueAt, remark);
        return id != null ? success("抢修工单已创建，等待巡检员接单")
                : error("建单失败：险情描述与负责人不能为空，且负责人须为在册用户");
    }

    @Log(title = "抢修工单接单", action = "claim")
    @ApiOperation(value = "接单", notes = "巡检员领取工单，待接单 → 已接单")
    @PostMapping("/claim")
    @RequiresPermissions("gasRepairOrder:claim")
    @ResponseBody
    public AjaxResult claim(Long id, String remark) {
        gasRepairOrderService.claim(id, ShiroUtils.getUser(), remark);
        return success("接单成功，请尽快赶赴现场开始抢修");
    }

    @Log(title = "抢修工单开始抢修", action = "start")
    @ApiOperation(value = "开始抢修", notes = "已接单 → 抢修中")
    @PostMapping("/start")
    @RequiresPermissions("gasRepairOrder:start")
    @ResponseBody
    public AjaxResult start(Long id, String remark) {
        gasRepairOrderService.start(id, ShiroUtils.getUser(), remark);
        return success("已开始抢修");
    }

    @Log(title = "抢修工单提交结果", action = "submitResult")
    @ApiOperation(value = "提交抢修结果", notes = "抢修中 → 待复核，必须填写抢修结果")
    @PostMapping("/submitResult")
    @RequiresPermissions("gasRepairOrder:submit")
    @ResponseBody
    public AjaxResult submitResult(Long id, String repairResult, String remark) {
        gasRepairOrderService.submitResult(id, ShiroUtils.getUser(), repairResult, remark);
        return success("抢修结果已提交，等待现场复核");
    }

    @Log(title = "抢修工单现场复核", action = "review")
    @ApiOperation(value = "现场复核", notes = "待复核 → 待封卡")
    @PostMapping("/review")
    @RequiresPermissions("gasRepairOrder:review")
    @ResponseBody
    public AjaxResult review(Long id, String remark) {
        gasRepairOrderService.review(id, ShiroUtils.getUser(), remark);
        return success("现场复核完成，等待完工封卡");
    }

    @Log(title = "抢修工单完工封卡", action = "seal")
    @ApiOperation(value = "完工封卡", notes = "待封卡 → 已封存，封卡后永久封存不得再修改")
    @PostMapping("/seal")
    @RequiresPermissions("gasRepairOrder:seal")
    @ResponseBody
    public AjaxResult seal(Long id, String remark) {
        gasRepairOrderService.seal(id, ShiroUtils.getUser(), remark);
        return success("已完工封卡，工单永久封存");
    }

    @ApiOperation(value = "处置履历跳转", notes = "处置履历跳转")
    @GetMapping("/recordView")
    @RequiresPermissions("gasRepairOrder:list")
    public String recordView(Long orderId, ModelMap model) {
        model.put("orderId", orderId);
        return prefix + "/record";
    }

    @Log(title = "抢修工单处置履历查询", action = "records")
    @ApiOperation(value = "处置履历", notes = "按操作时间升序返回该单全部过口留痕")
    @GetMapping("/records")
    @RequiresPermissions("gasRepairOrder:list")
    @ResponseBody
    public ResultTable records(Long orderId) {
        return dataTable(gasRepairOrderService.listRecords(orderId));
    }
}
