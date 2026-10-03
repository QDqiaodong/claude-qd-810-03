package com.drill.crew.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.Mine;
import com.drill.crew.dto.MineStatDTO;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.MineRepository;

/**
 * 矿区与钻孔。
 */
@Service
public class MineService {

    /** 勘查阶段 */
    public static final Set<String> STAGES = Set.of("预查", "普查", "详查", "勘探");
    /** 钻孔状态 */
    public static final Set<String> HOLE_STATUS = Set.of("施工", "终孔", "封孔");
    /** 实际孔深允许超出设计孔深的比例 */
    private static final BigDecimal OVER_DEPTH_RATIO = new BigDecimal("1.20");

    private final MineRepository mineRepository;
    private final BoreholeRepository boreholeRepository;
    private final HandoverLockService handoverLock;

    public MineService(MineRepository mineRepository, BoreholeRepository boreholeRepository,
                       HandoverLockService handoverLock) {
        this.mineRepository = mineRepository;
        this.boreholeRepository = boreholeRepository;
        this.handoverLock = handoverLock;
    }

    @Transactional(readOnly = true)
    public List<Mine> listMines() {
        return mineRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Mine getMine(Long id) {
        return mineRepository.findById(id).orElseThrow(() -> new BizException("矿区不存在：id=" + id));
    }

    @Transactional
    public Mine createMine(Mine mine) {
        requireText(mine.getCode(), "矿区编号不能为空");
        requireText(mine.getName(), "矿区名称不能为空");
        if (!STAGES.contains(mine.getStage())) {
            throw new BizException("勘查阶段只能为：预查 / 普查 / 详查 / 勘探");
        }
        if (mineRepository.existsByCode(mine.getCode())) {
            throw new BizException("矿区编号已存在：" + mine.getCode());
        }
        mine.setId(null);
        return mineRepository.save(mine);
    }

    @Transactional
    public Mine updateMine(Long id, Mine payload) {
        Mine mine = getMine(id);
        if (payload.getStage() != null && !STAGES.contains(payload.getStage())) {
            throw new BizException("勘查阶段只能为：预查 / 普查 / 详查 / 勘探");
        }
        if (payload.getName() != null) {
            requireText(payload.getName(), "矿区名称不能为空");
            mine.setName(payload.getName());
        }
        if (payload.getStage() != null) {
            mine.setStage(payload.getStage());
        }
        if (payload.getLocation() != null) {
            mine.setLocation(payload.getLocation());
        }
        if (payload.getAreaKm2() != null) {
            mine.setAreaKm2(payload.getAreaKm2());
        }
        if (payload.getChiefGeologist() != null) {
            mine.setChiefGeologist(payload.getChiefGeologist());
        }
        if (payload.getStartedOn() != null) {
            mine.setStartedOn(payload.getStartedOn());
        }
        if (payload.getRemark() != null) {
            mine.setRemark(payload.getRemark());
        }
        return mineRepository.save(mine);
    }

    @Transactional
    public void deleteMine(Long id) {
        Mine mine = getMine(id);
        long holes = boreholeRepository.findByMineIdOrderByHoleNoAsc(id).size();
        if (holes > 0) {
            throw new BizException("矿区【" + mine.getName() + "】下仍有 " + holes + " 个钻孔，不能删除");
        }
        mineRepository.delete(mine);
    }

    // ---------------- 钻孔 ----------------

    @Transactional(readOnly = true)
    public List<Borehole> listBoreholes(Long mineId) {
        if (mineId == null) {
            return boreholeRepository.findAll();
        }
        return boreholeRepository.findByMineIdOrderByHoleNoAsc(mineId);
    }

    @Transactional(readOnly = true)
    public Borehole getBorehole(Long id) {
        return boreholeRepository.findById(id).orElseThrow(() -> new BizException("钻孔不存在：id=" + id));
    }

    @Transactional
    public Borehole createBorehole(Borehole hole) {
        validateBorehole(hole, true);
        hole.setId(null);
        return boreholeRepository.save(hole);
    }

    @Transactional
    public Borehole updateBorehole(Long id, Borehole payload) {
        Borehole hole = getBorehole(id);
        // 已接收冻结期间，实际孔深 / 孔状态 / 终孔日期这些直接影响交接结论的字段禁止改写；
        // 坐标、倾角、备注等台账元数据不受此限。
        boolean touchesFrozenData = payload.getActualDepth() != null
                || payload.getStatus() != null
                || payload.getFinalDate() != null;
        if (touchesFrozenData) {
            handoverLock.requireWritable(id, "修改孔深、孔状态或终孔日期");
        }
        if (payload.getHoleNo() != null && !payload.getHoleNo().equals(hole.getHoleNo())
                && boreholeRepository.existsByHoleNo(payload.getHoleNo())) {
            throw new BizException("孔号已存在：" + payload.getHoleNo());
        }
        mergeBorehole(hole, payload);
        validateBorehole(hole, false);
        return boreholeRepository.save(hole);
    }

    @Transactional
    public void deleteBorehole(Long id) {
        Borehole hole = getBorehole(id);
        if ("施工".equals(hole.getStatus())) {
            throw new BizException("钻孔【" + hole.getHoleNo() + "】正在施工，不能删除");
        }
        handoverLock.requireDeletable(id);
        boreholeRepository.delete(hole);
    }

    // ---------------- 统计 ----------------

    /**
     * 手写 JPQL ①的结果：按矿区统计孔数与累计进尺。
     */
    @Transactional(readOnly = true)
    public List<MineStatDTO> mineStats() {
        List<MineStatDTO> stats = mineRepository.statHoleCountAndFootage();
        Map<Long, List<Borehole>> byMine = boreholeRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(Borehole::getMineId));
        for (MineStatDTO stat : stats) {
            List<Borehole> holes = byMine.getOrDefault(stat.getMineId(), List.of());
            BigDecimal total = nvl(stat.getTotalFootage());
            long count = stat.getHoleCount() == null ? 0 : stat.getHoleCount();
            stat.setAvgDepth(count == 0 ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
            stat.setMaxDepth(holes.stream().map(Borehole::getActualDepth).filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
        }
        return stats;
    }

    // ---------------- 规则 ----------------

    private void mergeBorehole(Borehole hole, Borehole payload) {
        if (payload.getMineId() != null) {
            hole.setMineId(payload.getMineId());
        }
        if (payload.getRigId() != null) {
            hole.setRigId(payload.getRigId());
        }
        if (payload.getHoleNo() != null) {
            hole.setHoleNo(payload.getHoleNo());
        }
        if (payload.getCoordX() != null) {
            hole.setCoordX(payload.getCoordX());
        }
        if (payload.getCoordY() != null) {
            hole.setCoordY(payload.getCoordY());
        }
        if (payload.getDesignDepth() != null) {
            hole.setDesignDepth(payload.getDesignDepth());
        }
        if (payload.getActualDepth() != null) {
            hole.setActualDepth(payload.getActualDepth());
        }
        if (payload.getSpudDate() != null) {
            hole.setSpudDate(payload.getSpudDate());
        }
        if (payload.getFinalDate() != null) {
            hole.setFinalDate(payload.getFinalDate());
        }
        if (payload.getCollarElev() != null) {
            hole.setCollarElev(payload.getCollarElev());
        }
        if (payload.getDipAngle() != null) {
            hole.setDipAngle(payload.getDipAngle());
        }
        if (payload.getStatus() != null) {
            hole.setStatus(payload.getStatus());
        }
    }

    private void validateBorehole(Borehole hole, boolean isCreate) {
        requireText(hole.getHoleNo(), "孔号不能为空");
        if (hole.getMineId() == null || !mineRepository.existsById(hole.getMineId())) {
            throw new BizException("所属矿区不存在，请选择有效矿区");
        }
        if (hole.getDesignDepth() == null || hole.getDesignDepth().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("设计孔深必须大于 0");
        }
        if (hole.getActualDepth() == null || hole.getActualDepth().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("实际孔深不能为负数");
        }
        BigDecimal limit = hole.getDesignDepth().multiply(OVER_DEPTH_RATIO).setScale(2, RoundingMode.HALF_UP);
        if (hole.getActualDepth().compareTo(limit) > 0) {
            throw new BizException("实际孔深 " + hole.getActualDepth() + " m 超出设计孔深允许范围（≤ " + limit + " m）");
        }
        if (isCreate && boreholeRepository.existsByHoleNo(hole.getHoleNo())) {
            throw new BizException("孔号已存在：" + hole.getHoleNo());
        }
        if (!HOLE_STATUS.contains(hole.getStatus())) {
            throw new BizException("钻孔状态只能为：施工 / 终孔 / 封孔");
        }
        if (!"施工".equals(hole.getStatus()) && hole.getFinalDate() == null) {
            throw new BizException("终孔或封孔状态的钻孔必须填写终孔日期");
        }
        if ("封孔".equals(hole.getStatus()) && hole.getActualDepth().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("封孔钻孔的实际孔深必须大于 0");
        }
        if (hole.getSpudDate() != null && hole.getFinalDate() != null
                && hole.getFinalDate().isBefore(hole.getSpudDate())) {
            throw new BizException("终孔日期不能早于开孔日期");
        }
    }

    static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(message);
        }
    }

    static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
