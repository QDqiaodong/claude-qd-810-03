package com.drill.crew.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CrewTeam;
import com.drill.crew.domain.MaterialItem;
import com.drill.crew.domain.ShiftReport;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CrewTeamRepository;
import com.drill.crew.repository.RigRepository;
import com.drill.crew.repository.ShiftReportRepository;

/**
 * 钻探班报表（materials 为 JSON 材料消耗明细）。
 */
@Service
public class ShiftReportService {

    /** 班次时长（小时）：纯钻 + 辅助不得超过该值 */
    public static final BigDecimal SHIFT_HOURS = new BigDecimal("8");
    /** 材料类别 */
    public static final Set<String> MATERIAL_CATEGORY = Set.of("钻头", "泥浆材料");

    private final ShiftReportRepository reportRepository;
    private final BoreholeRepository boreholeRepository;
    private final RigRepository rigRepository;
    private final CrewTeamRepository crewTeamRepository;
    private final HandoverLockService handoverLock;

    public ShiftReportService(ShiftReportRepository reportRepository, BoreholeRepository boreholeRepository,
                              RigRepository rigRepository, CrewTeamRepository crewTeamRepository,
                              HandoverLockService handoverLock) {
        this.reportRepository = reportRepository;
        this.boreholeRepository = boreholeRepository;
        this.rigRepository = rigRepository;
        this.crewTeamRepository = crewTeamRepository;
        this.handoverLock = handoverLock;
    }

    @Transactional(readOnly = true)
    public List<ShiftReport> listReports(Long rigId, Long boreholeId) {
        if (rigId != null) {
            return reportRepository.findByRigIdOrderByReportDateDesc(rigId);
        }
        if (boreholeId != null) {
            return reportRepository.findByBoreholeIdOrderByReportDateDesc(boreholeId);
        }
        return reportRepository.findAllByOrderByReportDateDesc();
    }

    @Transactional(readOnly = true)
    public ShiftReport getReport(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new BizException("班报不存在：id=" + id));
    }

    @Transactional
    public ShiftReport createReport(ShiftReport report) {
        validateReport(report);
        handoverLock.requireWritable(report.getBoreholeId(), "新增班报");
        report.setId(null);
        report.setCreatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    @Transactional
    public ShiftReport updateReport(Long id, ShiftReport payload) {
        ShiftReport report = getReport(id);
        handoverLock.requireWritable(report.getBoreholeId(), "修改班报");
        if (payload.getBoreholeId() != null && !payload.getBoreholeId().equals(report.getBoreholeId())) {
            handoverLock.requireWritable(payload.getBoreholeId(), "把班报改挂到该钻孔");
        }
        if (payload.getBoreholeId() != null) {
            report.setBoreholeId(payload.getBoreholeId());
        }
        if (payload.getRigId() != null) {
            report.setRigId(payload.getRigId());
        }
        if (payload.getCrewId() != null) {
            report.setCrewId(payload.getCrewId());
        }
        if (payload.getReportDate() != null) {
            report.setReportDate(payload.getReportDate());
        }
        if (payload.getShiftType() != null) {
            report.setShiftType(payload.getShiftType());
        }
        if (payload.getFootage() != null) {
            report.setFootage(payload.getFootage());
        }
        if (payload.getPureHours() != null) {
            report.setPureHours(payload.getPureHours());
        }
        if (payload.getAuxHours() != null) {
            report.setAuxHours(payload.getAuxHours());
        }
        if (payload.getAccidentNote() != null) {
            report.setAccidentNote(payload.getAccidentNote());
        }
        if (payload.getHandoverNote() != null) {
            report.setHandoverNote(payload.getHandoverNote());
        }
        if (payload.getMaterials() != null) {
            report.setMaterials(payload.getMaterials());
        }
        if (payload.getLeaderSign() != null) {
            report.setLeaderSign(payload.getLeaderSign());
        }
        validateReport(report);
        return reportRepository.save(report);
    }

    @Transactional
    public void deleteReport(Long id) {
        ShiftReport report = getReport(id);
        handoverLock.requireWritable(report.getBoreholeId(), "删除班报");
        reportRepository.delete(report);
    }

    /** 台班利用率：纯钻工时 / 班次时长 */
    public static BigDecimal utilization(ShiftReport report) {
        BigDecimal pure = MineService.nvl(report.getPureHours());
        return pure.multiply(BigDecimal.valueOf(100)).divide(SHIFT_HOURS, 2, RoundingMode.HALF_UP);
    }

    // ---------------- 规则 ----------------

    private void validateReport(ShiftReport report) {
        if (report.getBoreholeId() == null) {
            throw new BizException("班报必须指定钻孔");
        }
        Borehole hole = boreholeRepository.findById(report.getBoreholeId())
                .orElseThrow(() -> new BizException("钻孔不存在：id=" + report.getBoreholeId()));
        if ("封孔".equals(hole.getStatus())) {
            throw new BizException("钻孔【" + hole.getHoleNo() + "】已封孔，不再产生班报");
        }
        if (report.getRigId() == null || !rigRepository.existsById(report.getRigId())) {
            throw new BizException("钻机不存在，请选择有效钻机");
        }
        if (report.getReportDate() == null) {
            throw new BizException("班报日期不能为空");
        }
        if (!RigService.SHIFT_TYPE.contains(report.getShiftType())) {
            throw new BizException("班别只能为：早 / 中 / 夜");
        }
        if (report.getCrewId() != null) {
            CrewTeam crew = crewTeamRepository.findById(report.getCrewId())
                    .orElseThrow(() -> new BizException("班组不存在：id=" + report.getCrewId()));
            if (!crew.getRigId().equals(report.getRigId())) {
                throw new BizException("班组【" + crew.getLeader() + "】不属于所选钻机，班报钻机与班组必须一致");
            }
            if (report.getLeaderSign() == null || report.getLeaderSign().trim().isEmpty()) {
                report.setLeaderSign(crew.getLeader());
            }
        }
        if (report.getFootage() == null || report.getFootage().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("进尺不能为负数");
        }
        BigDecimal pure = MineService.nvl(report.getPureHours());
        BigDecimal aux = MineService.nvl(report.getAuxHours());
        if (pure.compareTo(BigDecimal.ZERO) < 0 || aux.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("纯钻时间与辅助时间不能为负数");
        }
        if (pure.add(aux).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("纯钻时间 + 辅助时间必须大于 0");
        }
        if (pure.add(aux).compareTo(SHIFT_HOURS) > 0) {
            throw new BizException("纯钻时间 " + pure + " h + 辅助时间 " + aux + " h = " + pure.add(aux)
                    + " h，超过班次时长 " + SHIFT_HOURS + " h");
        }
        List<MaterialItem> materials = report.getMaterials();
        if (materials != null) {
            for (int i = 0; i < materials.size(); i++) {
                MaterialItem item = materials.get(i);
                MineService.requireText(item.getName(), "第 " + (i + 1) + " 项材料名称不能为空");
                if (item.getCategory() == null || !MATERIAL_CATEGORY.contains(item.getCategory())) {
                    throw new BizException("第 " + (i + 1) + " 项材料类别只能为：钻头 / 泥浆材料");
                }
                if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("第 " + (i + 1) + " 项材料数量必须大于 0");
                }
            }
        }
    }
}
