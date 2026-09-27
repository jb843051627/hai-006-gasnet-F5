package com.fc.v2.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.fc.v2.model.auto.TGasImpRow;
import com.fc.v2.model.custom.GasLeakImportRow;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;

/**
 * 检漏数据导入 Excel 工具：上传解析 / 错误报告导出 / 导入模板导出。
 * 解析只负责把单元格归一化为原始文本（数字去科学计数法、日期格式化），业务校验在服务层。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class GasLeakExcelUtil {

    /** 模板/报告的表头（顺序即列顺序） */
    public static final String HEAD_SITE_NO = "管段编号";
    public static final String HEAD_DETECT_TIME = "检测时间";
    public static final String HEAD_CONC = "浓度值(ppm)";
    public static final String HEAD_LNG = "经度";
    public static final String HEAD_LAT = "纬度";
    public static final String HEAD_ITEM = "设备编号(选填)";
    public static final String HEAD_REMARK = "备注(选填)";

    /** 字段代号 → 表头别名（已归一化：去空白、全角括号转半角、小写） */
    private static final Map<String, List<String>> HEADER_ALIAS = new LinkedHashMap<String, List<String>>();

    static {
        HEADER_ALIAS.put("siteNo", Arrays.asList("管段编号", "管段号", "管段id", "siteno"));
        HEADER_ALIAS.put("detectTime", Arrays.asList("检测时间", "检测日期", "检测时刻", "detecttime"));
        HEADER_ALIAS.put("conc", Arrays.asList("浓度值", "浓度", "浓度值(ppm)", "浓度(ppm)", "conc"));
        HEADER_ALIAS.put("lng", Arrays.asList("经度", "lng", "longitude"));
        HEADER_ALIAS.put("lat", Arrays.asList("纬度", "lat", "latitude"));
        HEADER_ALIAS.put("itemCode", Arrays.asList("设备编号", "设备号", "设备/项代号", "检测项代号", "项代号"));
        HEADER_ALIAS.put("remark", Arrays.asList("备注"));
    }

    /** 必须存在的表头列（缺失则整份文件拒绝） */
    private static final List<String> REQUIRED_FIELDS = Arrays.asList("siteNo", "detectTime", "conc", "lng", "lat");

    /** 字段代号 → 缺列时的提示名 */
    private static final Map<String, String> FIELD_LABEL = new LinkedHashMap<String, String>();

    static {
        FIELD_LABEL.put("siteNo", HEAD_SITE_NO);
        FIELD_LABEL.put("detectTime", HEAD_DETECT_TIME);
        FIELD_LABEL.put("conc", HEAD_CONC);
        FIELD_LABEL.put("lng", HEAD_LNG);
        FIELD_LABEL.put("lat", HEAD_LAT);
    }

    private static final ThreadLocal<SimpleDateFormat> TS =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    private GasLeakExcelUtil() {
    }

    /**
     * 解析上传的检漏数据 Excel（第一个工作表，首行为表头）。
     * 表头按别名映射定位列，列顺序不限；空行跳过；行号为 Excel 实际行号（1起）。
     *
     * @param in 文件流（调用方负责关闭）
     * @return 数据行（不含表头；全空白行被跳过）
     * @throws IllegalArgumentException 文件无法读取、缺少必需表头列
     */
    public static List<GasLeakImportRow> parse(InputStream in) {
        Workbook wb;
        try {
            wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new IllegalArgumentException("文件无法解析，请上传有效的 .xlsx/.xls 检漏数据文件");
        }
        try {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null || sheet.getLastRowNum() < sheet.getFirstRowNum()) {
                throw new IllegalArgumentException("Excel 内容为空，未读取到表头");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> colIndex = locateColumns(headerRow, wb);

            List<GasLeakImportRow> rows = new ArrayList<GasLeakImportRow>();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                GasLeakImportRow item = new GasLeakImportRow();
                item.setRowNo(r + 1);
                item.setSiteNo(cellText(row == null ? null : row.getCell(colIndex.get("siteNo")), evaluator));
                item.setDetectTimeRaw(cellText(row == null ? null : row.getCell(colIndex.get("detectTime")), evaluator));
                item.setConcRaw(cellText(row == null ? null : row.getCell(colIndex.get("conc")), evaluator));
                item.setLngRaw(cellText(row == null ? null : row.getCell(colIndex.get("lng")), evaluator));
                item.setLatRaw(cellText(row == null ? null : row.getCell(colIndex.get("lat")), evaluator));
                Integer itemIdx = colIndex.get("itemCode");
                item.setItemCode(itemIdx == null || row == null ? "" : cellText(row.getCell(itemIdx), evaluator));
                Integer remarkIdx = colIndex.get("remark");
                item.setRemark(remarkIdx == null || row == null ? "" : cellText(row.getCell(remarkIdx), evaluator));
                if (isBlankRow(item)) {
                    continue;
                }
                rows.add(item);
            }
            return rows;
        } finally {
            try {
                wb.close();
            } catch (Exception ignore) {
                // 关闭失败不影响解析结果
            }
        }
    }

    /**
     * 导出校验失败行错误报告（.xlsx）：行号/管段编号/检测时间/浓度值/经度/纬度/错误原因。
     *
     * @param errors 失败行（按行号升序）
     * @param out    输出流（写出后不关闭，由调用方关闭）
     */
    public static void writeErrorReport(List<TGasImpRow> errors, OutputStream out) {
        ExcelWriter writer = ExcelUtil.getWriter();
        try {
            writer.writeHeadRow(Arrays.asList("行号", HEAD_SITE_NO, HEAD_DETECT_TIME, HEAD_CONC, HEAD_LNG, HEAD_LAT, "错误原因"));
            for (TGasImpRow e : errors) {
                writer.writeRow(Arrays.asList(
                        e.getRowNo(),
                        nullToEmpty(e.getSiteNo()),
                        nullToEmpty(e.getDetectTimeRaw()),
                        nullToEmpty(e.getConcRaw()),
                        nullToEmpty(e.getLngRaw()),
                        nullToEmpty(e.getLatRaw()),
                        nullToEmpty(e.getErrMsg())));
            }
            for (int i = 0; i < 7; i++) {
                writer.autoSizeColumn(i);
            }
            writer.flush(out);
        } finally {
            writer.close();
        }
    }

    /**
     * 导出导入模板（.xlsx）：表头 + 两行示例（备注列提示导入前删除）。
     *
     * @param out 输出流（写出后不关闭，由调用方关闭）
     */
    public static void writeTemplate(OutputStream out) {
        ExcelWriter writer = ExcelUtil.getWriter();
        try {
            writer.writeHeadRow(Arrays.asList(HEAD_SITE_NO, HEAD_DETECT_TIME, HEAD_CONC, HEAD_LNG, HEAD_LAT, HEAD_ITEM, HEAD_REMARK));
            writer.writeRow(Arrays.asList("GP00", "2026-09-20 10:30:00", "15.5", "116.397128", "39.916527", "UAV-01", "示例行(无人机采集)，导入前请删除"));
            writer.writeRow(Arrays.asList("GP00", "2026-09-20 11:05:00", "3.2", "116.398800", "39.917100", "LCV-03", "示例行(激光检测车采集)，导入前请删除"));
            for (int i = 0; i < 7; i++) {
                writer.autoSizeColumn(i);
            }
            writer.flush(out);
        } finally {
            writer.close();
        }
    }

    /** 表头归一化：去空白、全角括号转半角、转小写 */
    private static String normalizeHeader(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("（", "(").replace("）", ")")
                .replaceAll("\\s+", "").trim().toLowerCase();
    }

    /** 按别名定位各字段列号；缺必需列时抛出带缺失列名的异常 */
    private static Map<String, Integer> locateColumns(Row headerRow, Workbook wb) {
        Map<String, Integer> colIndex = new LinkedHashMap<String, Integer>();
        if (headerRow != null) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (Cell cell : headerRow) {
                String head = normalizeHeader(cellText(cell, evaluator));
                if (head.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, List<String>> entry : HEADER_ALIAS.entrySet()) {
                    if (colIndex.containsKey(entry.getKey())) {
                        continue;
                    }
                    for (String alias : entry.getValue()) {
                        if (head.equals(alias) || head.startsWith(alias + "(")) {
                            colIndex.put(entry.getKey(), cell.getColumnIndex());
                            break;
                        }
                    }
                }
            }
        }
        List<String> missing = new ArrayList<String>();
        for (String field : REQUIRED_FIELDS) {
            if (!colIndex.containsKey(field)) {
                missing.add(FIELD_LABEL.get(field));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Excel 缺少必需表头列：" + String.join("、", missing)
                    + "（标准表头：管段编号、检测时间、浓度值(ppm)、经度、纬度）");
        }
        return colIndex;
    }

    /** 单元格归一化为原始文本：字符串去首尾空白；数字去科学计数法；日期格式化；公式先求值 */
    private static String cellText(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            CellValue value = evaluator.evaluate(cell);
            if (value == null) {
                return "";
            }
            switch (value.getCellType()) {
                case STRING:
                    return trim(value.getStringValue());
                case NUMERIC:
                    return numberText(value.getNumberValue());
                case BOOLEAN:
                    return String.valueOf(value.getBooleanValue());
                default:
                    return "";
            }
        }
        switch (type) {
            case STRING:
                return trim(cell.getStringCellValue());
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell) && cell.getDateCellValue() != null) {
                    return TS.get().format(cell.getDateCellValue());
                }
                return numberText(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    /** 数字 → 文本：避免 1.2E7 这类科学计数法进入错误报告 */
    private static String numberText(double d) {
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 五个必填列全空白视为空行（尾部残留空行不参与校验） */
    private static boolean isBlankRow(GasLeakImportRow item) {
        return isEmpty(item.getSiteNo()) && isEmpty(item.getDetectTimeRaw()) && isEmpty(item.getConcRaw())
                && isEmpty(item.getLngRaw()) && isEmpty(item.getLatRaw())
                && isEmpty(item.getItemCode()) && isEmpty(item.getRemark());
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
