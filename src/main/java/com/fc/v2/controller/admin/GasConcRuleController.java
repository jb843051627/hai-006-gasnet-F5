package com.fc.v2.controller.admin;

import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGasConcRule;
import com.fc.v2.service.ITGasConcRuleService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 泄漏浓度分档判定线 Controller（规则版本配置 + 当前在用版本查询）
 *
 * @author fuce
 * @date 2026-09-26
 */
@Api(value = "泄漏浓度分档判定线")
@Controller
@RequestMapping("/gasConcRule")
public class GasConcRuleController extends BaseController {

    private final String prefix = "admin/gasConcRule";

    @Autowired
    private ITGasConcRuleService gasConcRuleService;

    @ApiOperation(value = "规则台账跳转", notes = "规则台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("gasConcRule:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "浓度分档规则台账查询", action = "list")
    @ApiOperation(value = "规则台账", notes = "规则台账（含历史版本）")
    @GetMapping("/list")
    @RequiresPermissions("gasConcRule:list")
    @ResponseBody
    public ResultTable list(TGasConcRule record) {
        QueryWrapper<TGasConcRule> queryWrapper = new QueryWrapper<TGasConcRule>();
        queryWrapper.eq("del_flag", 0);
        if (record.getRuleCode() != null && !record.getRuleCode().isEmpty()) {
            queryWrapper.like("rule_code", record.getRuleCode());
        }
        if (record.getRuleName() != null && !record.getRuleName().isEmpty()) {
            queryWrapper.like("rule_name", record.getRuleName());
        }
        if (record.getStatus() != null) {
            queryWrapper.eq("status", record.getStatus());
        }
        queryWrapper.orderByAsc("rule_code").orderByDesc("version");
        startPage();
        com.github.pagehelper.PageInfo<TGasConcRule> page =
                new com.github.pagehelper.PageInfo<TGasConcRule>(gasConcRuleService.selectTGasConcRuleList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "新增规则跳转", notes = "新增规则代号（首个版本）跳转")
    @GetMapping("/add")
    @RequiresPermissions("gasConcRule:add")
    public String add(ModelMap model) {
        model.put("formAction", "add");
        return prefix + "/add";
    }

    @Log(title = "浓度分档规则新增", action = "add")
    @ApiOperation(value = "新增规则", notes = "新建规则代号并发布首个版本")
    @PostMapping("/add")
    @RequiresPermissions("gasConcRule:add")
    @ResponseBody
    public AjaxResult add(TGasConcRule rule) {
        try {
            return toAjax(gasConcRuleService.insertTGasConcRule(rule));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "发布新版本跳转", notes = "为已有代号发布新版本跳转")
    @GetMapping("/addVersion")
    @RequiresPermissions("gasConcRule:addVersion")
    public String addVersion(String ruleCode, ModelMap model) {
        model.put("ruleCode", ruleCode);
        return prefix + "/addVersion";
    }

    @Log(title = "浓度分档规则发布新版本", action = "addVersion")
    @ApiOperation(value = "发布新版本", notes = "发布新版本并自动让旧版本交棒")
    @PostMapping("/addVersion")
    @RequiresPermissions("gasConcRule:addVersion")
    @ResponseBody
    public AjaxResult addVersion(TGasConcRule rule) {
        try {
            return toAjax(gasConcRuleService.publishNewVersion(rule));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "编辑规则跳转", notes = "编辑规则跳转")
    @GetMapping("/edit")
    @RequiresPermissions("gasConcRule:edit")
    public String edit(Long id, ModelMap model) {
        model.put("rule", gasConcRuleService.selectTGasConcRuleById(id));
        return prefix + "/edit";
    }

    @Log(title = "浓度分档规则编辑", action = "edit")
    @ApiOperation(value = "编辑规则", notes = "未生效版本可改阈值；已生效版本仅可改名称/优先级/备注")
    @PostMapping("/edit")
    @RequiresPermissions("gasConcRule:edit")
    @ResponseBody
    public AjaxResult edit(TGasConcRule rule) {
        try {
            return toAjax(gasConcRuleService.updateTGasConcRule(rule));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "浓度分档规则停用", action = "retire")
    @ApiOperation(value = "停用规则版本", notes = "立即交棒停用；指定 effEnd 则于该时刻停用")
    @PostMapping("/retire")
    @RequiresPermissions("gasConcRule:retire")
    @ResponseBody
    public AjaxResult retire(Long id, Date effEnd) {
        try {
            return toAjax(gasConcRuleService.retire(id, effEnd, ShiroUtils.getLoginName()));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "浓度分档规则删除", action = "remove")
    @ApiOperation(value = "删除规则版本", notes = "逻辑删除，历史评定记录的版本快照不受影响")
    @DeleteMapping("/remove")
    @RequiresPermissions("gasConcRule:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(gasConcRuleService.deleteTGasConcRuleByIds(ids));
    }
}
