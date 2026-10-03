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
import com.drill.crew.domain.CrewTeam;
import com.drill.crew.domain.Rig;
import com.drill.crew.dto.RigEfficiencyDTO;
import com.drill.crew.service.RigService;

/**
 * 模块二：钻机与班组。钻机 specs 为 JSON 属性包。
 */
@RestController
@RequestMapping("/api")
public class RigController {

    private final RigService rigService;

    public RigController(RigService rigService) {
        this.rigService = rigService;
    }

    // ---------------- 钻机 ----------------

    @GetMapping("/rigs")
    public ApiResponse<List<Rig>> listRigs() {
        return ApiResponse.success(rigService.listRigs());
    }

    @GetMapping("/rigs/{id}")
    public ApiResponse<Rig> getRig(@PathVariable Long id) {
        return ApiResponse.success(rigService.getRig(id));
    }

    @PostMapping("/rigs")
    public ApiResponse<Rig> createRig(@RequestBody Rig rig) {
        return ApiResponse.success("钻机已创建", rigService.createRig(rig));
    }

    @PutMapping("/rigs/{id}")
    public ApiResponse<Rig> updateRig(@PathVariable Long id, @RequestBody Rig rig) {
        return ApiResponse.success("钻机已更新", rigService.updateRig(id, rig));
    }

    @DeleteMapping("/rigs/{id}")
    public ApiResponse<Void> deleteRig(@PathVariable Long id) {
        rigService.deleteRig(id);
        return ApiResponse.success("钻机已删除", null);
    }

    /** JPQL ③：按钻机统计台月效率 */
    @GetMapping("/rigs/efficiency")
    public ApiResponse<List<RigEfficiencyDTO>> efficiency() {
        return ApiResponse.success(rigService.efficiencyStats());
    }

    // ---------------- 班组 ----------------

    @GetMapping("/crews")
    public ApiResponse<List<CrewTeam>> listCrews(@RequestParam(required = false) Long rigId) {
        return ApiResponse.success(rigService.listCrews(rigId));
    }

    @PostMapping("/crews")
    public ApiResponse<CrewTeam> createCrew(@RequestBody CrewTeam crew) {
        return ApiResponse.success("班组已创建", rigService.createCrew(crew));
    }

    @DeleteMapping("/crews/{id}")
    public ApiResponse<Void> deleteCrew(@PathVariable Long id) {
        rigService.deleteCrew(id);
        return ApiResponse.success("班组已删除", null);
    }
}
