package com.drill.crew.controller;

import java.util.List;
import java.util.Map;

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
import com.drill.crew.domain.CoreRound;
import com.drill.crew.domain.LithoLayer;
import com.drill.crew.dto.LithoStatDTO;
import com.drill.crew.service.CoreLogService;

/**
 * 模块三：岩芯编录（本仓核心）。litho_layer.attrs 为 JSON 属性包。
 */
@RestController
@RequestMapping("/api")
public class CoreLogController {

    private final CoreLogService coreLogService;

    public CoreLogController(CoreLogService coreLogService) {
        this.coreLogService = coreLogService;
    }

    // ---------------- 回次 ----------------

    @GetMapping("/core-rounds")
    public ApiResponse<List<CoreRound>> listRounds(@RequestParam(required = false) Long boreholeId) {
        return ApiResponse.success(coreLogService.listRounds(boreholeId));
    }

    @GetMapping("/core-rounds/{id}")
    public ApiResponse<CoreRound> getRound(@PathVariable Long id) {
        return ApiResponse.success(coreLogService.getRound(id));
    }

    @PostMapping("/core-rounds")
    public ApiResponse<CoreRound> createRound(@RequestBody CoreRound round) {
        return ApiResponse.success("回次已录入", coreLogService.createRound(round));
    }

    @PutMapping("/core-rounds/{id}")
    public ApiResponse<CoreRound> updateRound(@PathVariable Long id, @RequestBody CoreRound round) {
        return ApiResponse.success("回次已更新", coreLogService.updateRound(id, round));
    }

    @DeleteMapping("/core-rounds/{id}")
    public ApiResponse<Void> deleteRound(@PathVariable Long id) {
        coreLogService.deleteRound(id);
        return ApiResponse.success("回次已删除", null);
    }

    // ---------------- 岩性分层 ----------------

    @GetMapping("/litho-layers")
    public ApiResponse<List<LithoLayer>> listLayers(@RequestParam(required = false) Long boreholeId,
                                                    @RequestParam(required = false) Long roundId) {
        return ApiResponse.success(coreLogService.listLayers(boreholeId, roundId));
    }

    @GetMapping("/litho-layers/{id}")
    public ApiResponse<LithoLayer> getLayer(@PathVariable Long id) {
        return ApiResponse.success(coreLogService.getLayer(id));
    }

    @PostMapping("/litho-layers")
    public ApiResponse<LithoLayer> createLayer(@RequestBody LithoLayer layer) {
        return ApiResponse.success("岩性分层已录入", coreLogService.createLayer(layer));
    }

    @PutMapping("/litho-layers/{id}")
    public ApiResponse<LithoLayer> updateLayer(@PathVariable Long id, @RequestBody LithoLayer layer) {
        return ApiResponse.success("岩性分层已更新", coreLogService.updateLayer(id, layer));
    }

    /** 只更新 JSON 属性包 attrs（含水性 / 硬度 / 裂隙发育 / 矿化 / 蚀变 / 取样编号 …） */
    @PutMapping("/litho-layers/{id}/attrs")
    public ApiResponse<LithoLayer> updateAttrs(@PathVariable Long id, @RequestBody Map<String, Object> attrs) {
        return ApiResponse.success("属性包已保存", coreLogService.updateAttrs(id, attrs));
    }

    @DeleteMapping("/litho-layers/{id}")
    public ApiResponse<Void> deleteLayer(@PathVariable Long id) {
        coreLogService.deleteLayer(id);
        return ApiResponse.success("岩性分层已删除", null);
    }

    // ---------------- 统计 ----------------

    /** JPQL ②：按岩性统计厚度占比 */
    @GetMapping("/core-logs/lithology-stats")
    public ApiResponse<List<LithoStatDTO>> lithologyStats() {
        return ApiResponse.success(coreLogService.lithologyStats());
    }

    /** 低采取率回次（编录质量核查） */
    @GetMapping("/core-logs/low-recovery")
    public ApiResponse<List<CoreRound>> lowRecovery() {
        return ApiResponse.success(coreLogService.lowRecoveryRounds());
    }
}
