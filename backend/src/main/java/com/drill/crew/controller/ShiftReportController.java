package com.drill.crew.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drill.crew.common.ApiResponse;
import com.drill.crew.domain.MaterialItem;
import com.drill.crew.domain.ShiftReport;
import com.drill.crew.service.ShiftReportService;

/**
 * 模块五：钻探班报表。materials 为 JSON 材料消耗明细。
 */
@RestController
@RequestMapping("/api/shift-reports")
public class ShiftReportController {

    private final ShiftReportService reportService;

    public ShiftReportController(ShiftReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<List<ShiftReport>> listReports(@RequestParam(required = false) Long rigId,
                                                      @RequestParam(required = false) Long boreholeId) {
        return ApiResponse.success(reportService.listReports(rigId, boreholeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShiftReport> getReport(@PathVariable Long id) {
        return ApiResponse.success(reportService.getReport(id));
    }

    /** 原样返回 JSON 列解析出的材料消耗明细 */
    @GetMapping("/{id}/materials")
    public ApiResponse<List<MaterialItem>> materials(@PathVariable Long id) {
        return ApiResponse.success(reportService.getReport(id).getMaterials());
    }

    @PostMapping
    public ApiResponse<ShiftReport> createReport(@RequestBody ShiftReport report) {
        return ApiResponse.success("班报已保存", reportService.createReport(report));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShiftReport> updateReport(@PathVariable Long id, @RequestBody ShiftReport report) {
        return ApiResponse.success("班报已更新", reportService.updateReport(id, report));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ApiResponse.success("班报已删除", null);
    }
}
