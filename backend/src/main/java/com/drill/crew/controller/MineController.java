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
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.Mine;
import com.drill.crew.dto.MineStatDTO;
import com.drill.crew.service.MineService;

/**
 * 模块一：矿区与钻孔。
 */
@RestController
@RequestMapping("/api")
public class MineController {

    private final MineService mineService;

    public MineController(MineService mineService) {
        this.mineService = mineService;
    }

    // ---------------- 矿区 ----------------

    @GetMapping("/mines")
    public ApiResponse<List<Mine>> listMines() {
        return ApiResponse.success(mineService.listMines());
    }

    @GetMapping("/mines/{id}")
    public ApiResponse<Mine> getMine(@PathVariable Long id) {
        return ApiResponse.success(mineService.getMine(id));
    }

    @PostMapping("/mines")
    public ApiResponse<Mine> createMine(@RequestBody Mine mine) {
        return ApiResponse.success("矿区已创建", mineService.createMine(mine));
    }

    @PutMapping("/mines/{id}")
    public ApiResponse<Mine> updateMine(@PathVariable Long id, @RequestBody Mine mine) {
        return ApiResponse.success("矿区已更新", mineService.updateMine(id, mine));
    }

    @DeleteMapping("/mines/{id}")
    public ApiResponse<Void> deleteMine(@PathVariable Long id) {
        mineService.deleteMine(id);
        return ApiResponse.success("矿区已删除", null);
    }

    /** JPQL ①：按矿区统计孔数与累计进尺 */
    @GetMapping("/mines/stats")
    public ApiResponse<List<MineStatDTO>> mineStats() {
        return ApiResponse.success(mineService.mineStats());
    }

    // ---------------- 钻孔 ----------------

    @GetMapping("/boreholes")
    public ApiResponse<List<Borehole>> listBoreholes(@RequestParam(required = false) Long mineId) {
        return ApiResponse.success(mineService.listBoreholes(mineId));
    }

    @GetMapping("/boreholes/{id}")
    public ApiResponse<Borehole> getBorehole(@PathVariable Long id) {
        return ApiResponse.success(mineService.getBorehole(id));
    }

    @PostMapping("/boreholes")
    public ApiResponse<Borehole> createBorehole(@RequestBody Borehole borehole) {
        return ApiResponse.success("钻孔已创建", mineService.createBorehole(borehole));
    }

    @PutMapping("/boreholes/{id}")
    public ApiResponse<Borehole> updateBorehole(@PathVariable Long id, @RequestBody Borehole borehole) {
        return ApiResponse.success("钻孔已更新", mineService.updateBorehole(id, borehole));
    }

    @DeleteMapping("/boreholes/{id}")
    public ApiResponse<Void> deleteBorehole(@PathVariable Long id) {
        mineService.deleteBorehole(id);
        return ApiResponse.success("钻孔已删除", null);
    }
}
