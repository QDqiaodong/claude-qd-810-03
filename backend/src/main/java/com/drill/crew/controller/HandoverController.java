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
import com.drill.crew.domain.Handover;
import com.drill.crew.dto.HandoverAction;
import com.drill.crew.dto.IssueView;
import com.drill.crew.service.HandoverService;

/**
 * 模块六：钻孔终孔交接台。
 * 班组从施工钻孔发起交接；地质负责人在同一处查看孔深 / 岩芯编录 / 钻进参数 / 班报完整性，
 * 退回整改或确认接收；接收后可凭原因返开。历史版本只读、可还原。
 */
@RestController
@RequestMapping("/api/handovers")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 交接台列表（各钻孔当前生效版本，可按状态过滤） */
    @GetMapping
    public ApiResponse<List<Handover>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(handoverService.listActive(status));
    }

    /** 钻孔的历史版本链 */
    @GetMapping("/borehole/{boreholeId}/versions")
    public ApiResponse<List<Handover>> versions(@PathVariable Long boreholeId) {
        return ApiResponse.success(handoverService.listVersions(boreholeId));
    }

    /** 版本详情：元数据 + 发起时资料快照 + 本版本问题项 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(handoverService.getDetail(id));
    }

    /** 按实时资料检查钻孔（负责人检查页 / 整改后刷新，不写库） */
    @GetMapping("/borehole/{boreholeId}/live-issues")
    public ApiResponse<List<IssueView>> liveIssues(@PathVariable Long boreholeId) {
        return ApiResponse.success(handoverService.liveIssues(boreholeId));
    }

    /** 班组：从施工（终孔）钻孔发起交接 */
    @PostMapping("/borehole/{boreholeId}/initiate")
    public ApiResponse<Handover> initiate(@PathVariable Long boreholeId, @RequestBody(required = false) HandoverAction action) {
        return ApiResponse.success("交接已发起，等待地质负责人检查",
                handoverService.initiate(boreholeId, action == null ? new HandoverAction() : action));
    }

    /** 班组：保存整改进度（问题未清零也允许，但不推进到可接收） */
    @PostMapping("/{id}/save-progress")
    public ApiResponse<Map<String, Object>> saveProgress(@PathVariable Long id,
                                                         @RequestBody(required = false) HandoverAction action) {
        return ApiResponse.success("整改进度已保存",
                handoverService.saveProgress(id, action == null ? new HandoverAction() : action));
    }

    /** 班组：整改后重新提交（产生新版本） */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<Handover> resubmit(@PathVariable Long id, @RequestBody(required = false) HandoverAction action) {
        return ApiResponse.success("已重新提交，形成新的交接版本",
                handoverService.resubmit(id, action == null ? new HandoverAction() : action));
    }

    /** 负责人：退回整改（必须填写原因） */
    @PostMapping("/{id}/reject")
    public ApiResponse<Handover> reject(@PathVariable Long id, @RequestBody HandoverAction action) {
        return ApiResponse.success("已退回班组整改", handoverService.reject(id, action));
    }

    /** 负责人：确认接收（待处理问题必须清零） */
    @PostMapping("/{id}/accept")
    public ApiResponse<Handover> accept(@PathVariable Long id, @RequestBody(required = false) HandoverAction action) {
        return ApiResponse.success("已确认接收，该孔资料已冻结",
                handoverService.accept(id, action == null ? new HandoverAction() : action));
    }

    /** 负责人：接收后返开（必须填写原因，原接收版本标记已失效） */
    @PostMapping("/{id}/reopen")
    public ApiResponse<Handover> reopen(@PathVariable Long id, @RequestBody HandoverAction action) {
        return ApiResponse.success("已返开，原接收版本标记为已失效", handoverService.reopen(id, action));
    }
}
