package com.drill.crew.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drill.crew.common.ApiResponse;
import com.drill.crew.domain.DrillParamSeries;
import com.drill.crew.domain.ParamPoint;
import com.drill.crew.service.DrillParamService;

/**
 * 模块四：钻进参数时序。points 为 JSON 多点采样数组。
 */
@RestController
@RequestMapping("/api/param-series")
public class DrillParamController {

    private final DrillParamService paramService;

    public DrillParamController(DrillParamService paramService) {
        this.paramService = paramService;
    }

    @GetMapping
    public ApiResponse<List<DrillParamSeries>> listSeries(@RequestParam(required = false) Long boreholeId) {
        return ApiResponse.success(paramService.listSeries(boreholeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<DrillParamSeries> getSeries(@PathVariable Long id) {
        return ApiResponse.success(paramService.getSeries(id));
    }

    /** 原样返回 JSON 列解析出的采样点数组 */
    @GetMapping("/{id}/points")
    public ApiResponse<List<ParamPoint>> points(@PathVariable Long id) {
        return ApiResponse.success(paramService.getSeries(id).getPoints());
    }

    /** 时序分析：叠加异常判定与孔内情况备注 */
    @GetMapping("/{id}/analysis")
    public ApiResponse<Map<String, Object>> analysis(@PathVariable Long id) {
        return ApiResponse.success(paramService.analyze(id));
    }

    @PostMapping
    public ApiResponse<DrillParamSeries> createSeries(@RequestBody DrillParamSeries series) {
        return ApiResponse.success("时序已录入", paramService.createSeries(series));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSeries(@PathVariable Long id) {
        paramService.deleteSeries(id);
        return ApiResponse.success("时序已删除", null);
    }
}
