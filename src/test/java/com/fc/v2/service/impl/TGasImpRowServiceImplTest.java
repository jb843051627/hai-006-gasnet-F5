package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TGasImpRowMapper;
import com.fc.v2.mapper.auto.TGasPipeMapper;
import com.fc.v2.model.auto.TGasImpRow;
import com.fc.v2.model.auto.TGasLeakCheck;
import com.fc.v2.model.auto.TGasPipe;
import com.fc.v2.model.custom.GasLeakImportResult;
import com.fc.v2.service.ITGasLeakCheckService;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;

/**
 * 检漏数据批量导入：逐行校验（管段存在性/时间格式/浓度数字/经纬度范围）、
 * 同批次业务键去重、合格行入库、失败行错误原因留痕。
 * 测试用 hutool 真实生成 xlsx，走完整 解析→校验→入库 链路。
 */
@ExtendWith(MockitoExtension.class)
class TGasImpRowServiceImplTest {

    private static final String[] HEADER =
            {"管段编号", "检测时间", "浓度值(ppm)", "经度", "纬度", "设备编号(选填)", "备注(选填)"};

    @Mock
    private TGasImpRowMapper gasImpRowMapper;
    @Mock
    private TGasPipeMapper gasPipeMapper;
    @Mock
    private ITGasLeakCheckService gasLeakCheckService;

    @InjectMocks
    private TGasImpRowServiceImpl service;

    private static InputStream excel(String[] header, List<List<Object>> rows) {
        ExcelWriter writer = ExcelUtil.getWriter();
        writer.writeHeadRow(Arrays.asList(header));
        for (List<Object> row : rows) {
            writer.writeRow(row);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.flush(out);
        writer.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    private static TGasPipe pipe(String siteNo, String siteName) {
        TGasPipe p = new TGasPipe();
        p.setSiteNo(siteNo);
        p.setSiteName(siteName);
        return p;
    }

    private static Date at(String ts) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ts);
    }

    /** 管段底档 + 同批次无已入库记录（多数用例的公共前置） */
    private void stubPipesAndNoExisting() {
        when(gasPipeMapper.selectList(any())).thenReturn(Arrays.asList(
                pipe("GP00", "青坪市城区中压主干管段（在用）"), pipe("GP01", "青坪市城东调压站（已退役）")));
        when(gasLeakCheckService.selectTGasLeakCheckList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void importExcel_validRows_savedWithBatchNoAndPipeName() {
        stubPipesAndNoExisting();
        when(gasImpRowMapper.insert(any())).thenReturn(1);
        when(gasLeakCheckService.evaluateAndSave(any(), isNull())).thenAnswer(inv -> {
            TGasLeakCheck c = inv.getArgument(0);
            c.setId(900001L);
            return c;
        });

        InputStream in = excel(HEADER, Arrays.asList(
                row("GP00", "2026-09-20 10:30:00", "15.5", "116.397128", "39.916527", "UAV-01", "无人机普测"),
                row("GP01", "2026-09-20 11:05:00", "3.2", "116.398800", "39.917100", "", "")));

        GasLeakImportResult result = service.importExcel(null, in, "admin");

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getOkRows());
        assertEquals(0, result.getFailRows());
        assertEquals(0, result.getDupRows());
        assertTrue(result.getBatchNo().startsWith("IMP"));

        // 正式记录：批次号/管段名称/经纬度/浓度均已带入
        ArgumentCaptor<TGasLeakCheck> checkCaptor = ArgumentCaptor.forClass(TGasLeakCheck.class);
        verify(gasLeakCheckService, org.mockito.Mockito.times(2)).evaluateAndSave(checkCaptor.capture(), isNull());
        TGasLeakCheck saved = checkCaptor.getValue();
        assertEquals(result.getBatchNo(), saved.getBatchNo());
        assertEquals("GP01", saved.getSiteNo());
        assertEquals("青坪市城东调压站（已退役）", saved.getSiteName());
        assertEquals(new BigDecimal("3.20"), saved.getConcValue());
        assertEquals(new BigDecimal("116.398800"), saved.getLng());
        assertEquals(new BigDecimal("39.917100"), saved.getLat());

        // 核对行：两行均 已入库(1) 且回填 check_id
        ArgumentCaptor<TGasImpRow> rowCaptor = ArgumentCaptor.forClass(TGasImpRow.class);
        verify(gasImpRowMapper, org.mockito.Mockito.times(2)).insert(rowCaptor.capture());
        List<TGasImpRow> staged = rowCaptor.getAllValues();
        assertEquals(Integer.valueOf(1), staged.get(0).getStatus());
        assertEquals(Long.valueOf(900001L), staged.get(0).getCheckId());
        assertEquals("UAV-01", staged.get(0).getItemCode());
        assertEquals(Integer.valueOf(2), staged.get(0).getRowNo());
    }

    @Test
    void importExcel_excelDateAndNumericCells_parsedAsRawText() throws Exception {
        stubPipesAndNoExisting();
        when(gasImpRowMapper.insert(any())).thenReturn(1);
        when(gasLeakCheckService.evaluateAndSave(any(), isNull())).thenAnswer(inv -> {
            TGasLeakCheck c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        // Excel 原生日期单元格 + 数字单元格（无人机导出的常见形态）
        InputStream in = excel(HEADER, Collections.singletonList(
                row("GP00", at("2026-09-20 10:30:00"), 15.5d, 116.397128d, 39.916527d, "", "")));

        GasLeakImportResult result = service.importExcel("B-CELL", in, "admin");
        assertEquals(1, result.getOkRows());

        ArgumentCaptor<TGasLeakCheck> captor = ArgumentCaptor.forClass(TGasLeakCheck.class);
        verify(gasLeakCheckService).evaluateAndSave(captor.capture(), isNull());
        assertEquals(at("2026-09-20 10:30:00"), captor.getValue().getDetectTime());
        assertEquals(new BigDecimal("15.50"), captor.getValue().getConcValue());
    }

    @Test
    void importExcel_timeTextVariants_accepted() {
        stubPipesAndNoExisting();
        when(gasImpRowMapper.insert(any())).thenReturn(1);
        when(gasLeakCheckService.evaluateAndSave(any(), isNull())).thenAnswer(inv -> {
            TGasLeakCheck c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        InputStream in = excel(HEADER, Arrays.asList(
                row("GP00", "2026/09/20 10:30", "1", "116.1", "39.1", "", ""),
                row("GP00", "2026-09-20", "2", "116.2", "39.2", "", ""),
                row("GP00", "2026-09-20 10:30:00", "3", "116.3", "39.3", "", "")));

        GasLeakImportResult result = service.importExcel("B-TIME", in, "admin");
        assertEquals(3, result.getOkRows());
        assertEquals(0, result.getFailRows());
    }

    @Test
    void importExcel_invalidRows_collectAllErrorReasons() {
        stubPipesAndNoExisting();
        when(gasImpRowMapper.insert(any())).thenReturn(1);

        InputStream in = excel(HEADER, Arrays.asList(
                // 管段不存在 + 时间格式错 + 浓度非数字 + 经纬度超范围
                row("NOPE", "2026年9月20日", "abc", "999", "-91", "", ""),
                // 必填项为空 + 浓度为负
                row("", "", "-0.5", "", "", "", "")));

        GasLeakImportResult result = service.importExcel("B-BAD", in, "admin");
        assertEquals(2, result.getTotalRows());
        assertEquals(0, result.getOkRows());
        assertEquals(2, result.getFailRows());

        ArgumentCaptor<TGasImpRow> captor = ArgumentCaptor.forClass(TGasImpRow.class);
        verify(gasImpRowMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<TGasImpRow> staged = captor.getAllValues();

        TGasImpRow first = staged.get(0);
        assertEquals(Integer.valueOf(2), first.getStatus());
        assertTrue(first.getErrMsg().contains("管段编号不存在：NOPE"));
        assertTrue(first.getErrMsg().contains("检测时间格式错误：2026年9月20日"));
        assertTrue(first.getErrMsg().contains("浓度值不是有效数字：abc"));
        assertTrue(first.getErrMsg().contains("经度超出合理范围(-180~180)：999"));
        assertTrue(first.getErrMsg().contains("纬度超出合理范围(-90~90)：-91"));
        // 原始文本留痕，供错误报告导出
        assertEquals("abc", first.getConcRaw());
        assertEquals("2026年9月20日", first.getDetectTimeRaw());

        TGasImpRow second = staged.get(1);
        assertTrue(second.getErrMsg().contains("管段编号不能为空"));
        assertTrue(second.getErrMsg().contains("检测时间不能为空"));
        assertTrue(second.getErrMsg().contains("浓度值不能为负：-0.5"));
        assertTrue(second.getErrMsg().contains("经度不能为空"));
        assertTrue(second.getErrMsg().contains("纬度不能为空"));

        // 全部失败 → 不产生正式记录
        verify(gasLeakCheckService, never()).evaluateAndSave(any(), any());
    }

    @Test
    void importExcel_sameBatchReupload_dedupByBusinessKey() throws Exception {
        when(gasPipeMapper.selectList(any())).thenReturn(Collections.singletonList(pipe("GP00", "青坪市城区中压主干管段（在用）")));
        // 同批次已入库一条（业务键：GP00|2026-09-20 10:30:00|15.50|116.397128|39.916527）
        TGasLeakCheck existing = new TGasLeakCheck();
        existing.setSiteNo("GP00");
        existing.setDetectTime(at("2026-09-20 10:30:00"));
        existing.setConcValue(new BigDecimal("15.50"));
        existing.setLng(new BigDecimal("116.397128"));
        existing.setLat(new BigDecimal("39.916527"));
        when(gasLeakCheckService.selectTGasLeakCheckList(any()))
                .thenReturn(Collections.singletonList(existing));
        when(gasImpRowMapper.delete(any())).thenReturn(1);
        when(gasImpRowMapper.insert(any())).thenReturn(1);
        when(gasLeakCheckService.evaluateAndSave(any(), isNull())).thenAnswer(inv -> {
            TGasLeakCheck c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        InputStream in = excel(HEADER, Arrays.asList(
                row("GP00", "2026-09-20 10:30:00", "15.5", "116.397128", "39.916527", "", ""),   // 与已入库重复
                row("GP00", "2026-09-20 10:30:00", "15.50", "116.3971280", "39.9165270", "", ""), // 文件内重复（刻度不同值相同）
                row("GP00", "2026-09-20 11:00:00", "15.5", "116.397128", "39.916527", "", ""))); // 新数据

        GasLeakImportResult result = service.importExcel("B-DUP", in, "admin");
        assertEquals(3, result.getTotalRows());
        assertEquals(1, result.getOkRows());
        assertEquals(2, result.getDupRows());
        assertEquals(0, result.getFailRows());
        assertEquals("B-DUP", result.getBatchNo());

        // 重传先清理上一轮 退回/重复跳过 行
        verify(gasImpRowMapper).delete(any());
        // 仅新数据行入库
        verify(gasLeakCheckService, org.mockito.Mockito.times(1)).evaluateAndSave(any(), isNull());

        ArgumentCaptor<TGasImpRow> captor = ArgumentCaptor.forClass(TGasImpRow.class);
        verify(gasImpRowMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertEquals(Integer.valueOf(3), captor.getAllValues().get(0).getStatus());
        assertEquals(Integer.valueOf(3), captor.getAllValues().get(1).getStatus());
        assertEquals(Integer.valueOf(1), captor.getAllValues().get(2).getStatus());
    }

    @Test
    void importExcel_emptySheet_rejected() {
        InputStream in = excel(HEADER, Collections.emptyList());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(null, in, "admin"));
        assertTrue(e.getMessage().contains("没有数据行"));
    }

    @Test
    void importExcel_missingRequiredHeader_rejected() {
        InputStream in = excel(new String[]{"管段编号", "检测时间"},
                Collections.singletonList(row("GP00", "2026-09-20 10:30:00")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(null, in, "admin"));
        assertTrue(e.getMessage().contains("缺少必需表头列"));
        assertTrue(e.getMessage().contains("浓度值(ppm)"));
    }

    @Test
    void importExcel_overMaxRows_rejected() {
        List<List<Object>> rows = new ArrayList<List<Object>>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("GP00", "2026-09-20 10:30:00", "1", "116.1", "39.1", "", ""));
        }
        InputStream in = excel(HEADER, rows);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.importExcel(null, in, "admin"));
        assertTrue(e.getMessage().contains("单批最多"));
        verify(gasImpRowMapper, never()).insert(any());
    }

    @Test
    void importExcel_blankRowsSkipped_notCounted() {
        stubPipesAndNoExisting();
        when(gasImpRowMapper.insert(any())).thenReturn(1);
        when(gasLeakCheckService.evaluateAndSave(any(), isNull())).thenAnswer(inv -> {
            TGasLeakCheck c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        InputStream in = excel(HEADER, Arrays.asList(
                row("GP00", "2026-09-20 10:30:00", "1", "116.1", "39.1", "", ""),
                row("", "", "", "", "", "", "")));

        GasLeakImportResult result = service.importExcel("B-BLANK", in, "admin");
        assertEquals(1, result.getTotalRows());
        assertEquals(1, result.getOkRows());
    }
}
