package com.drill.crew.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drill.crew.common.ApiResponse;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.SampleBatch;
import com.drill.crew.dto.BatchPackRequest;
import com.drill.crew.dto.BatchReceiveRequest;
import com.drill.crew.dto.BatchReturnRequest;
import com.drill.crew.service.SampleBatchService;

/**
 * 模块七：岩芯样品送检批次。
 * 现场按送检批次封样运输；实验室接收时逐样核对状态、数量、封签后回写接收时间、状态与异常说明；
 * 支持整批退回。批次交接时展示样品明细，历史交接结果（事件链）可追溯。
 */
@RestController
@RequestMapping("/api/sample-batches")
public class SampleBatchController {

    private final SampleBatchService batchService;

    public SampleBatchController(SampleBatchService batchService) {
        this.batchService = batchService;
    }

    /** 批次列表（可按状态过滤：运输中 / 已接收 / 部分接收 / 已退回） */
    @GetMapping
    public ApiResponse<List<SampleBatch>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(batchService.list(status));
    }

    /** 批次详情：批次头 + 样品明细（行级接收结果）+ 交接事件链 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(batchService.detail(id));
    }

    /** 现场：封样发运（选定已登记样品生成送检批次，同时落样品明细与发运事件） */
    @PostMapping("/pack")
    public ApiResponse<Map<String, Object>> pack(@RequestBody BatchPackRequest request) {
        Map<String, Object> detail = batchService.pack(request);
        SampleBatch batch = (SampleBatch) detail.get("batch");
        return ApiResponse.success("批次 " + batch.getBatchNo() + " 已封样发运，共 "
                + batch.getExpectedCount() + " 件样品", detail);
    }

    /** 实验室：接收（全部一致 → 已接收；部分异常 → 部分接收，已接收样品保留、异常标待补） */
    @PostMapping("/{id}/receive")
    public ApiResponse<Map<String, Object>> receive(@PathVariable Long id,
                                                    @RequestBody BatchReceiveRequest request) {
        Map<String, Object> detail = batchService.receive(id, request);
        SampleBatch batch = (SampleBatch) detail.get("batch");
        String msg = SampleDict.B_PARTIAL.equals(batch.getStatus())
                ? "部分接收：已接收 " + batch.getReceivedCount() + " 件，异常待补 " + batch.getAbnormalCount() + " 件"
                : "批次 " + batch.getBatchNo() + " 全部接收（" + batch.getReceivedCount() + " 件，数量、封签一致）";
        return ApiResponse.success(msg, detail);
    }

    /** 实验室：整批退回（必须填退回原因；样品回到待送检可重新组批） */
    @PostMapping("/{id}/return")
    public ApiResponse<Map<String, Object>> returnBatch(@PathVariable Long id,
                                                        @RequestBody BatchReturnRequest request) {
        return ApiResponse.success("批次已整批退回，样品退回待送检，可补齐手续后重新组批",
                batchService.returnBatch(id, request));
    }
}
