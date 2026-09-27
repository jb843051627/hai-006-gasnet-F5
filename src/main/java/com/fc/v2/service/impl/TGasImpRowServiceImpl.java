package com.fc.v2.service.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TGasImpRowMapper;
import com.fc.v2.mapper.auto.TGasPipeMapper;
import com.fc.v2.model.auto.TGasImpRow;
import com.fc.v2.model.auto.TGasLeakCheck;
import com.fc.v2.model.auto.TGasPipe;
import com.fc.v2.model.custom.GasLeakImportResult;
import com.fc.v2.model.custom.GasLeakImportRow;
import com.fc.v2.service.ITGasImpRowService;
import com.fc.v2.service.ITGasLeakCheckService;
import com.fc.v2.util.GasLeakExcelUtil;

/**
 * 检漏数据导入核对行 Service业务层处理（batch-process 形状：整批提交）。
 * 口径：逐行校验全量收集错误；同批次按业务键（管段+检测时间+浓度+经纬度）去重；
 * 合格行复用 {@link ITGasLeakCheckService#evaluateAndSave} 判档入库，与手工录入同口径。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGasImpRowServiceImpl implements ITGasImpRowService {

    /** 单批数据行上限（超出整批拒绝，提示拆分） */
    private static final int MAX_ROWS = 500;

    /** 行落地情形：已入库 / 退回(校验失败) / 重复跳过 */
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;
    private static final int STATUS_DUP = 3;

    /** 删除标记：正常 */
    private static final int DEL_NORMAL = 0;

    /** 浓度存储上限（decimal(12,2) 最大绝对值） */
    private static final BigDecimal MAX_CONC = new BigDecimal("9999999999.99");
    /** 经度合理范围 */
    private static final BigDecimal LNG_MIN = new BigDecimal("-180");
    private static final BigDecimal LNG_MAX = new BigDecimal("180");
    /** 纬度合理范围 */
    private static final BigDecimal LAT_MIN = new BigDecimal("-90");
    private static final BigDecimal LAT_MAX = new BigDecimal("90");

    /** 检测时间可接受的录入格式（Excel 日期单元格已在解析层格式化为 yyyy-MM-dd HH:mm:ss） */
    private static final String[] TIME_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM/dd"
    };

    @javax.annotation.Resource
    private TGasImpRowMapper gasImpRowMapper;

    @javax.annotation.Resource
    private TGasPipeMapper gasPipeMapper;

    @javax.annotation.Resource
    private ITGasLeakCheckService gasLeakCheckService;

    @Override
    public TGasImpRow selectTGasImpRowById(Long id) {
        return this.gasImpRowMapper.selectById(id);
    }

    @Override
    public List<TGasImpRow> selectTGasImpRowList(QueryWrapper<TGasImpRow> queryWrapper) {
        return this.gasImpRowMapper.selectList(queryWrapper);
    }

    @Override
    public List<Map<String, Object>> listBatchSummary(String batchNo) {
        QueryWrapper<TGasImpRow> qw = new QueryWrapper<TGasImpRow>()
                .select("batch_no AS batchNo",
                        "COUNT(*) AS totalRows",
                        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS okRows",
                        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS failRows",
                        "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS dupRows",
                        "MAX(create_time) AS lastTime")
                .eq("del_flag", DEL_NORMAL)
                .groupBy("batch_no")
                .orderByDesc("lastTime");
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            qw.like("batch_no", batchNo.trim());
        }
        return this.gasImpRowMapper.selectMaps(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GasLeakImportResult importExcel(String batchNo, InputStream in, String operator) {
        List<GasLeakImportRow> rows = GasLeakExcelUtil.parse(in);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Excel 中没有数据行（首行须为表头）");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("单批最多 " + MAX_ROWS + " 行，本次读取到 " + rows.size() + " 行，请拆分后上传");
        }
        String no = (batchNo == null || batchNo.trim().isEmpty()) ? generateBatchNo() : batchNo.trim();

        // 管段底档快照：site_no → site_name（仅未删除；存在性校验以此为准）
        Map<String, String> pipeMap = new HashMap<String, String>();
        for (TGasPipe pipe : this.gasPipeMapper.selectList(
                new QueryWrapper<TGasPipe>().eq("del_flag", DEL_NORMAL))) {
            if (pipe.getSiteNo() != null) {
                pipeMap.put(pipe.getSiteNo().trim(), pipe.getSiteName());
            }
        }

        // 同批次已入库记录的业务键 → 重复上传自动去重（手工录入 batch_no 为 NULL，不参与）
        Set<String> dedupKeys = new HashSet<String>();
        for (TGasLeakCheck c : this.gasLeakCheckService.selectTGasLeakCheckList(
                new QueryWrapper<TGasLeakCheck>()
                        .eq("batch_no", no).eq("del_flag", DEL_NORMAL).eq("status", 0))) {
            dedupKeys.add(bizKey(c.getSiteNo(), c.getDetectTime(), c.getConcValue(), c.getLng(), c.getLat()));
        }

        // 修正重传：清掉上一轮的退回/重复跳过行，错误报告只反映本次校验结果（已入库行保留作痕）
        this.gasImpRowMapper.delete(new QueryWrapper<TGasImpRow>()
                .eq("batch_no", no).in("status", STATUS_FAIL, STATUS_DUP));

        int ok = 0;
        int fail = 0;
        int dup = 0;
        for (GasLeakImportRow row : rows) {
            List<String> errors = new ArrayList<String>();

            String siteNo = trim(row.getSiteNo());
            if (isBlank(siteNo)) {
                errors.add("管段编号不能为空");
            } else if (!pipeMap.containsKey(siteNo)) {
                errors.add("管段编号不存在：" + siteNo);
            }

            Date detectTime = null;
            if (isBlank(row.getDetectTimeRaw())) {
                errors.add("检测时间不能为空");
            } else {
                detectTime = parseTime(row.getDetectTimeRaw());
                if (detectTime == null) {
                    errors.add("检测时间格式错误：" + row.getDetectTimeRaw() + "（支持 yyyy-MM-dd HH:mm:ss）");
                }
            }

            BigDecimal conc = null;
            if (isBlank(row.getConcRaw())) {
                errors.add("浓度值不能为空");
            } else {
                conc = parseDecimal(row.getConcRaw());
                if (conc == null) {
                    errors.add("浓度值不是有效数字：" + row.getConcRaw());
                } else if (conc.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add("浓度值不能为负：" + row.getConcRaw());
                } else if (conc.compareTo(MAX_CONC) > 0) {
                    errors.add("浓度值超出合理范围(0~" + MAX_CONC.toPlainString() + ")：" + row.getConcRaw());
                } else {
                    conc = conc.setScale(2, RoundingMode.HALF_UP);
                }
            }

            BigDecimal lng = null;
            if (isBlank(row.getLngRaw())) {
                errors.add("经度不能为空");
            } else {
                lng = parseDecimal(row.getLngRaw());
                if (lng == null) {
                    errors.add("经度不是有效数字：" + row.getLngRaw());
                } else if (lng.compareTo(LNG_MIN) < 0 || lng.compareTo(LNG_MAX) > 0) {
                    errors.add("经度超出合理范围(-180~180)：" + row.getLngRaw());
                } else {
                    lng = lng.setScale(6, RoundingMode.HALF_UP);
                }
            }

            BigDecimal lat = null;
            if (isBlank(row.getLatRaw())) {
                errors.add("纬度不能为空");
            } else {
                lat = parseDecimal(row.getLatRaw());
                if (lat == null) {
                    errors.add("纬度不是有效数字：" + row.getLatRaw());
                } else if (lat.compareTo(LAT_MIN) < 0 || lat.compareTo(LAT_MAX) > 0) {
                    errors.add("纬度超出合理范围(-90~90)：" + row.getLatRaw());
                } else {
                    lat = lat.setScale(6, RoundingMode.HALF_UP);
                }
            }

            // 核对行留痕（原始文本 + 解析值 + 错误原因），成功/失败/重复都落一行
            TGasImpRow staging = new TGasImpRow();
            staging.setBatchNo(no);
            staging.setRowNo(row.getRowNo());
            staging.setItemCode(emptyToNull(row.getItemCode()));
            staging.setSiteNo(emptyToNull(siteNo));
            staging.setDetectTime(detectTime);
            staging.setDetectTimeRaw(emptyToNull(row.getDetectTimeRaw()));
            staging.setQty(conc);
            staging.setConcRaw(emptyToNull(row.getConcRaw()));
            staging.setLng(lng);
            staging.setLngRaw(emptyToNull(row.getLngRaw()));
            staging.setLat(lat);
            staging.setLatRaw(emptyToNull(row.getLatRaw()));
            staging.setRemark(truncate(row.getRemark(), 500));
            staging.setDelFlag(DEL_NORMAL);
            staging.setCreateBy(operator);

            if (!errors.isEmpty()) {
                staging.setStatus(STATUS_FAIL);
                staging.setErrMsg(truncate(String.join("；", errors), 500));
                this.gasImpRowMapper.insert(staging);
                fail++;
                continue;
            }

            // 业务键去重：同批次已入库（含本文件前序行）直接跳过
            if (!dedupKeys.add(bizKey(siteNo, detectTime, conc, lng, lat))) {
                staging.setStatus(STATUS_DUP);
                this.gasImpRowMapper.insert(staging);
                dup++;
                continue;
            }

            // 合格行 → 正式检测报告（按检测时刻在用规则版本判档，与手工录入同口径）
            TGasLeakCheck record = new TGasLeakCheck();
            record.setDetectTime(detectTime);
            record.setSiteNo(siteNo);
            record.setSiteName(pipeMap.get(siteNo));
            record.setConcValue(conc);
            record.setLng(lng);
            record.setLat(lat);
            record.setBatchNo(no);
            record.setRemark(truncate(row.getRemark(), 500));
            record.setCreateBy(operator);
            TGasLeakCheck saved = this.gasLeakCheckService.evaluateAndSave(record, null);

            staging.setStatus(STATUS_OK);
            staging.setCheckId(saved.getId());
            this.gasImpRowMapper.insert(staging);
            ok++;
        }
        return new GasLeakImportResult(no, rows.size(), ok, fail, dup);
    }

    @Override
    public List<TGasImpRow> listErrors(String batchNo) {
        return this.gasImpRowMapper.selectList(new QueryWrapper<TGasImpRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", DEL_NORMAL)
                .orderByAsc("row_no"));
    }

    /** 去重业务键：管段编号 + 检测时间(墙钟) + 浓度 + 经度 + 纬度（解析值均已归一化刻度） */
    private static String bizKey(String siteNo, Date detectTime, BigDecimal conc, BigDecimal lng, BigDecimal lat) {
        return siteNo + "|" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(detectTime)
                + "|" + conc.toPlainString() + "|" + lng.toPlainString() + "|" + lat.toPlainString();
    }

    /** 批次号：IMP + 时间戳 + 4位随机（未指定批次号时生成） */
    private static String generateBatchNo() {
        int rand = new java.util.Random().nextInt(10000);
        return "IMP" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + "-" + String.format("%04d", rand);
    }

    /** 多格式严格解析（lenient=false），全部不匹配返回 null */
    private static Date parseTime(String raw) {
        String s = raw.trim();
        for (String pattern : TIME_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(s);
            } catch (ParseException ignore) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    /** 有效数字解析，非法返回 null（接受科学计数法文本，如 1.2E3） */
    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
