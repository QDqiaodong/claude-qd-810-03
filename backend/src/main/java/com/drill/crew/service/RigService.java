package com.drill.crew.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CrewTeam;
import com.drill.crew.domain.Rig;
import com.drill.crew.dto.RigEfficiencyDTO;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CrewTeamRepository;
import com.drill.crew.repository.RigRepository;
import com.drill.crew.repository.ShiftReportRepository;

/**
 * 钻机与班组。
 */
@Service
public class RigService {

    /** 钻机状态 */
    public static final Set<String> RIG_STATUS = Set.of("运行", "搬迁", "维修", "停用");
    /** 值班班别 */
    public static final Set<String> SHIFT_TYPE = Set.of("早", "中", "夜");
    /** 一台钻机最多配置的班组数 */
    public static final int MAX_CREW_PER_RIG = 3;
    /** 单班人数区间 */
    public static final int MIN_WORKER = 2;
    public static final int MAX_WORKER = 8;
    /** 折算台月按 30 个台班、每班 8 小时计 */
    private static final BigDecimal MONTH_SHIFT_HOURS = new BigDecimal("240");

    private final RigRepository rigRepository;
    private final CrewTeamRepository crewTeamRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final BoreholeRepository boreholeRepository;

    public RigService(RigRepository rigRepository, CrewTeamRepository crewTeamRepository,
                      ShiftReportRepository shiftReportRepository, BoreholeRepository boreholeRepository) {
        this.rigRepository = rigRepository;
        this.crewTeamRepository = crewTeamRepository;
        this.shiftReportRepository = shiftReportRepository;
        this.boreholeRepository = boreholeRepository;
    }

    // ---------------- 钻机 ----------------

    @Transactional(readOnly = true)
    public List<Rig> listRigs() {
        return rigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Rig getRig(Long id) {
        return rigRepository.findById(id).orElseThrow(() -> new BizException("钻机不存在：id=" + id));
    }

    @Transactional
    public Rig createRig(Rig rig) {
        validateRig(rig, true);
        rig.setId(null);
        return rigRepository.save(rig);
    }

    @Transactional
    public Rig updateRig(Long id, Rig payload) {
        Rig rig = getRig(id);
        if (payload.getModel() != null) {
            MineService.requireText(payload.getModel(), "钻机型号不能为空");
            rig.setModel(payload.getModel());
        }
        if (payload.getMaxDepth() != null) {
            rig.setMaxDepth(payload.getMaxDepth());
        }
        if (payload.getEnginePower() != null) {
            rig.setEnginePower(payload.getEnginePower());
        }
        if (payload.getStatus() != null) {
            rig.setStatus(payload.getStatus());
        }
        if (payload.getManufacturer() != null) {
            rig.setManufacturer(payload.getManufacturer());
        }
        if (payload.getCommissionedOn() != null) {
            rig.setCommissionedOn(payload.getCommissionedOn());
        }
        if (payload.getSpecs() != null) {
            rig.setSpecs(payload.getSpecs());
        }
        validateRig(rig, false);
        return rigRepository.save(rig);
    }

    @Transactional
    public void deleteRig(Long id) {
        Rig rig = getRig(id);
        List<Borehole> holes = boreholeRepository.findAll().stream()
                .filter(h -> id.equals(h.getRigId()))
                .toList();
        boolean working = holes.stream().anyMatch(h -> "施工".equals(h.getStatus()));
        if (working) {
            throw new BizException("钻机【" + rig.getCode() + "】仍有施工中的钻孔，不能删除");
        }
        long crews = crewTeamRepository.findByRigIdOrderByIdAsc(id).size();
        if (crews > 0) {
            throw new BizException("钻机【" + rig.getCode() + "】下仍有 " + crews + " 个班组，请先移除");
        }
        rigRepository.delete(rig);
    }

    // ---------------- 班组 ----------------

    @Transactional(readOnly = true)
    public List<CrewTeam> listCrews(Long rigId) {
        if (rigId == null) {
            return crewTeamRepository.findAll();
        }
        return crewTeamRepository.findByRigIdOrderByIdAsc(rigId);
    }

    @Transactional
    public CrewTeam createCrew(CrewTeam crew) {
        validateCrew(crew);
        long existing = crewTeamRepository.findByRigIdOrderByIdAsc(crew.getRigId()).size();
        if (existing >= MAX_CREW_PER_RIG) {
            throw new BizException("一台钻机最多配置 " + MAX_CREW_PER_RIG + " 个班组");
        }
        crew.setId(null);
        return crewTeamRepository.save(crew);
    }

    @Transactional
    public void deleteCrew(Long id) {
        CrewTeam crew = crewTeamRepository.findById(id)
                .orElseThrow(() -> new BizException("班组不存在：id=" + id));
        crewTeamRepository.delete(crew);
    }

    // ---------------- 统计 ----------------

    /**
     * 手写 JPQL ③的结果：按钻机统计台月效率。
     */
    @Transactional(readOnly = true)
    public List<RigEfficiencyDTO> efficiencyStats() {
        List<RigEfficiencyDTO> list = shiftReportRepository.statEfficiencyByRig();
        for (RigEfficiencyDTO e : list) {
            BigDecimal footage = MineService.nvl(e.getTotalFootage());
            BigDecimal pure = MineService.nvl(e.getPureHours());
            BigDecimal aux = MineService.nvl(e.getAuxHours());
            long shifts = e.getShiftCount() == null ? 0 : e.getShiftCount();
            e.setFootagePerShift(shifts == 0 ? BigDecimal.ZERO
                    : footage.divide(BigDecimal.valueOf(shifts), 2, RoundingMode.HALF_UP));
            BigDecimal totalHours = pure.add(aux);
            e.setPureRatio(totalHours.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                    : pure.multiply(BigDecimal.valueOf(100)).divide(totalHours, 2, RoundingMode.HALF_UP));
            // 台月效率 = 累计进尺 / (纯钻工时 / 240 h 每台月)
            if (pure.compareTo(BigDecimal.ZERO) <= 0) {
                e.setMeterPerMonth(BigDecimal.ZERO);
            } else {
                BigDecimal months = pure.divide(MONTH_SHIFT_HOURS, 6, RoundingMode.HALF_UP);
                e.setMeterPerMonth(months.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                        : footage.divide(months, 2, RoundingMode.HALF_UP));
            }
        }
        return list;
    }

    // ---------------- 规则 ----------------

    private void validateRig(Rig rig, boolean isCreate) {
        MineService.requireText(rig.getCode(), "钻机编号不能为空");
        MineService.requireText(rig.getModel(), "钻机型号不能为空");
        if (rig.getMaxDepth() == null || rig.getMaxDepth().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("最大钻深必须大于 0");
        }
        if (rig.getEnginePower() == null || rig.getEnginePower().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("发动机功率必须大于 0");
        }
        if (!RIG_STATUS.contains(rig.getStatus())) {
            throw new BizException("钻机状态只能为：运行 / 搬迁 / 维修 / 停用");
        }
        if (isCreate && rigRepository.existsByCode(rig.getCode())) {
            throw new BizException("钻机编号已存在：" + rig.getCode());
        }
    }

    private void validateCrew(CrewTeam crew) {
        MineService.requireText(crew.getLeader(), "班组长不能为空");
        if (crew.getRigId() == null || !rigRepository.existsById(crew.getRigId())) {
            throw new BizException("所属钻机不存在，请选择有效钻机");
        }
        Rig rig = getRig(crew.getRigId());
        if ("停用".equals(rig.getStatus())) {
            throw new BizException("钻机【" + rig.getCode() + "】已停用，不能再配置班组");
        }
        if (!SHIFT_TYPE.contains(crew.getShiftType())) {
            throw new BizException("值班班别只能为：早 / 中 / 夜");
        }
        int workers = crew.getWorkerCount() == null ? 0 : crew.getWorkerCount();
        if (workers < MIN_WORKER || workers > MAX_WORKER) {
            throw new BizException("钻工人数应在 " + MIN_WORKER + "~" + MAX_WORKER + " 人之间");
        }
    }
}
