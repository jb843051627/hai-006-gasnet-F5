package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasStopBill;
import com.fc.v2.service.ITGasStopBillService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 燃气管段停用申请 Controller（approval-chain 形状：申请 → 多级签批 → 生效停用）
 *
 * 审批链：运维提 → 安全核 → 工程评 → 管理层批；
 * 支持退回补充材料与否决；终审通过后管段停用并取消后续巡检任务。
 *
 * @author fuce
 * @date 2026-09-26
 */
@Api(value = "燃气管段停用申请")
@Controller
@RequestMapping("/gasStopBill")
public class GasStopBillController extends BaseController {

    private final String prefix = "admin/gasStopBill";

    @Autowired
    private ITGasStopBillService gasStopBillService;

    @ApiOperation(value = "签核台账跳转", notes = "签核台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasStopBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "管段停用申请台账查询", action = "list")
    @ApiOperation(value = "签核台账", notes = "签核台账")
    @GetMapping("/list")
    @RequiresPermissions("gasStopBill:list")
    @ResponseBody
    public ResultTable list(TGasStopBill record) {
        QueryWrapper<TGasStopBill> queryWrapper = new QueryWrapper<TGasStopBill>();
        queryWrapper.eq("del_flag", 0);
        if (record.getBillNo() != null && !record.getBillNo().isEmpty()) {
            queryWrapper.like("bill_no", record.getBillNo());
        }
        if (record.getSiteNo() != null && !record.getSiteNo().isEmpty()) {
            queryWrapper.like("site_no", record.getSiteNo());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        queryWrapper.orderByDesc("id");
        startPage();
        com.github.pagehelper.PageInfo<TGasStopBill> page =
                new com.github.pagehelper.PageInfo<TGasStopBill>(gasStopBillService.selectTGasStopBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "发起申请跳转", notes = "发起申请跳转")
    @GetMapping("/addView")
    @RequiresPermissions("gasStopBill:apply")
    public String addView(ModelMap model) {
        model.put("pipes", gasStopBillService.listActivePipes());
        return prefix + "/add";
    }

    @Log(title = "管段停用申请提交", action = "apply")
    @ApiOperation(value = "发起停用申请", notes = "运维部门填写停用理由发起申请，提交后进入安全管理部门审核")
    @PostMapping("/apply")
    @RequiresPermissions("gasStopBill:apply")
    @ResponseBody
    public AjaxResult apply(Long siteId, String applyReason, Integer signMode) {
        int rows = gasStopBillService.apply(siteId, applyReason, signMode, ShiroUtils.getLoginName());
        return rows > 0 ? success("申请已提交，待安全管理部门审核")
                : error("申请失败：管段不存在、已停用或已有在核申请");
    }

    @Log(title = "管段停用申请签批", action = "approve")
    @ApiOperation(value = "签批通过", notes = "当前环节签批通过，末道（管理层批准）通过后管段停用生效")
    @PostMapping("/approve")
    @RequiresPermissions("gasStopBill:approve")
    @ResponseBody
    public AjaxResult approve(Long id, String opinion) {
        TGasStopBill bill = gasStopBillService.approve(id, ShiroUtils.getLoginName(), opinion);
        if (bill == null) {
            return error("签批失败：单据不存在或不在在核状态");
        }
        return bill.getStatus() != null && bill.getStatus() == 1
                ? success("审批完成，管段已停用并取消后续巡检任务") : success("已签批通过，流转下一环节");
    }

    @Log(title = "管段停用申请否决", action = "reject")
    @ApiOperation(value = "否决申请", notes = "否决后申请终止，不再流转")
    @PostMapping("/reject")
    @RequiresPermissions("gasStopBill:reject")
    @ResponseBody
    public AjaxResult reject(Long id, String opinion) {
        return gasStopBillService.reject(id, ShiroUtils.getLoginName(), opinion) != null
                ? success("已否决该申请") : error("否决失败：单据不存在或不在在核状态");
    }

    @Log(title = "管段停用申请退回", action = "rollback")
    @ApiOperation(value = "退回补充材料", notes = "退回运维申请人补充材料，补充后可重新提交")
    @PostMapping("/rollback")
    @RequiresPermissions("gasStopBill:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String opinion) {
        return gasStopBillService.rollback(id, ShiroUtils.getLoginName(), opinion) != null
                ? success("已退回申请人补充材料") : error("退回失败：单据不存在或不在在核状态");
    }

    @Log(title = "管段停用申请重新提交", action = "resubmit")
    @ApiOperation(value = "重新提交", notes = "被打回的申请补充材料后重新提交，重新进入安全管理部门审核")
    @PostMapping("/resubmit")
    @RequiresPermissions("gasStopBill:apply")
    @ResponseBody
    public AjaxResult resubmit(Long id, String applyReason) {
        return gasStopBillService.resubmit(id, applyReason, ShiroUtils.getLoginName()) != null
                ? success("已重新提交，待安全管理部门审核") : error("重新提交失败：单据不存在或未被打回");
    }

    @ApiOperation(value = "审批履历跳转", notes = "审批履历跳转")
    @GetMapping("/recordView")
    @RequiresPermissions("gasStopBill:list")
    public String recordView(Long billId, ModelMap model) {
        model.put("billId", billId);
        return prefix + "/record";
    }

    @Log(title = "管段停用申请履历查询", action = "records")
    @ApiOperation(value = "审批履历", notes = "按审批时间升序返回该单全部过口留痕")
    @GetMapping("/records")
    @RequiresPermissions("gasStopBill:list")
    @ResponseBody
    public ResultTable records(Long billId) {
        return dataTable(gasStopBillService.listRecords(billId));
    }
}
