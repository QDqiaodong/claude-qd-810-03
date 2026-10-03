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
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.dto.SamplePayload;
import com.drill.crew.service.SampleService;

/**
 * 模块七：岩芯样品取样登记。
 * 现场人员在指定孔段登记样品编号、起止深度、取样人、封签号；
 * 关键信息齐全才登记为正式样品，缺关键信息只保存草稿。
 */
@RestController
@RequestMapping("/api/core-samples")
public class CoreSampleController {

    private final SampleService sampleService;

    public CoreSampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    /** 样品台账：可按钻孔、状态（DRAFT/REGISTERED/IN_TRANSIT/RECEIVED/ABNORMAL）筛选 */
    @GetMapping
    public ApiResponse<List<CoreSample>> list(@RequestParam(required = false) Long boreholeId,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.success(sampleService.list(boreholeId, status));
    }

    /** 待送检散样（组批选择器）：可按钻孔过滤 */
    @GetMapping("/packable")
    public ApiResponse<List<CoreSample>> packable(@RequestParam(required = false) Long boreholeId) {
        return ApiResponse.success(sampleService.listPackable(boreholeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CoreSample> get(@PathVariable Long id) {
        return ApiResponse.success(sampleService.get(id));
    }

    /**
     * 保存样品：后端按关键信息是否齐全决定登记为正式样品还是草稿。
     * 返回 message 会明确告知本次保存结果。
     */
    @PostMapping
    public ApiResponse<CoreSample> create(@RequestBody SamplePayload payload) {
        CoreSample saved = sampleService.create(payload);
        return ApiResponse.success(draftOrRegistered(saved), saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<CoreSample> update(@PathVariable Long id, @RequestBody SamplePayload payload) {
        CoreSample saved = sampleService.update(id, payload);
        return ApiResponse.success(draftOrRegistered(saved) + "（已更新）", saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestParam(required = false) Integer expectedVersion) {
        sampleService.delete(id, expectedVersion);
        return ApiResponse.success("样品已删除", null);
    }

    private String draftOrRegistered(CoreSample s) {
        return SampleDict.S_DRAFT.equals(s.getStatus())
                ? "关键信息不完整，已保存为草稿；补齐编号、深度、取样人、封签后将转为正式登记"
                : "样品已登记（编号、封签唯一，孔段未重叠）";
    }
}
