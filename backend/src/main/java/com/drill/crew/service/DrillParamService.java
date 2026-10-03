package com.drill.crew.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.DrillParamSeries;
import com.drill.crew.domain.ParamPoint;
import com.drill.crew.dto.ParamPointView;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.DrillParamSeriesRepository;

/**
 * 钻进参数时序（points 为 JSON 多点采样数组）。
 */
@Service
public class DrillParamService {

    /** 参数录入的物理上限 */
    private static final Map<String, BigDecimal[]> HARD_LIMIT = Map.of(
            "pressure", new BigDecimal[] { new BigDecimal("0"), new BigDecimal("200") },
            "rotation", new BigDecimal[] { new BigDecimal("0"), new BigDecimal("1500") },
            "pump", new BigDecimal[] { new BigDecimal("0"), new BigDecimal("600") },
            "mudDensity", new BigDecimal[] { new BigDecimal("0.80"), new BigDecimal("2.50") });

    /** 合理区间：越界即判定为异常点 */
    public static final Map<String, BigDecimal[]> NORMAL_RANGE = Map.of(
            "pressure", new BigDecimal[] { new BigDecimal("8"), new BigDecimal("45") },
            "rotation", new BigDecimal[] { new BigDecimal("60"), new BigDecimal("450") },
            "pump", new BigDecimal[] { new BigDecimal("60"), new BigDecimal("260") },
            "mudDensity", new BigDecimal[] { new BigDecimal("1.05"), new BigDecimal("1.35") });

    public static final Map<String, String> LABEL = Map.of(
            "pressure", "钻压",
            "rotation", "转速",
            "pump", "泵量",
            "mudDensity", "泥浆密度");

    private final DrillParamSeriesRepository seriesRepository;
    private final BoreholeRepository boreholeRepository;
    private final HandoverLockService handoverLock;

    public DrillParamService(DrillParamSeriesRepository seriesRepository, BoreholeRepository boreholeRepository,
                             HandoverLockService handoverLock) {
        this.seriesRepository = seriesRepository;
        this.boreholeRepository = boreholeRepository;
        this.handoverLock = handoverLock;
    }

    @Transactional(readOnly = true)
    public List<DrillParamSeries> listSeries(Long boreholeId) {
        if (boreholeId == null) {
            return seriesRepository.findAllByOrderByRecordDateDesc();
        }
        return seriesRepository.findByBoreholeIdOrderByRecordDateDesc(boreholeId);
    }

    @Transactional(readOnly = true)
    public DrillParamSeries getSeries(Long id) {
        return seriesRepository.findById(id).orElseThrow(() -> new BizException("参数时序不存在：id=" + id));
    }

    @Transactional
    public DrillParamSeries createSeries(DrillParamSeries series) {
        validateSeries(series);
        handoverLock.requireWritable(series.getBoreholeId(), "录入钻进参数时序");
        series.setId(null);
        series.setCreatedAt(LocalDateTime.now());
        return seriesRepository.save(series);
    }

    @Transactional
    public void deleteSeries(Long id) {
        DrillParamSeries series = getSeries(id);
        handoverLock.requireWritable(series.getBoreholeId(), "删除钻进参数时序");
        seriesRepository.delete(series);
    }

    /**
     * 时序分析：在原 JSON 采样点上叠加异常判定与越限项说明。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> analyze(Long id) {
        DrillParamSeries series = getSeries(id);
        Borehole hole = boreholeRepository.findById(series.getBoreholeId()).orElse(null);
        List<ParamPoint> points = series.getPoints() == null ? List.of() : series.getPoints();

        List<ParamPointView> views = new ArrayList<>();
        int abnormalCount = 0;
        for (int i = 0; i < points.size(); i++) {
            ParamPointView v = ParamPointView.of(i, points.get(i));
            List<String> items = new ArrayList<>();
            checkRange("pressure", v.getPressure(), items);
            checkRange("rotation", v.getRotation(), items);
            checkRange("pump", v.getPump(), items);
            checkRange("mudDensity", v.getMudDensity(), items);
            v.setAbnormal(!items.isEmpty());
            v.setAbnormalItems(items);
            if (v.isAbnormal()) {
                abnormalCount++;
            }
            views.add(v);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seriesId", series.getId());
        result.put("boreholeId", series.getBoreholeId());
        result.put("holeNo", hole == null ? null : hole.getHoleNo());
        result.put("mineId", hole == null ? null : hole.getMineId());
        result.put("recordDate", series.getRecordDate());
        result.put("shiftType", series.getShiftType());
        result.put("operator", series.getOperator());
        result.put("pointCount", points.size());
        result.put("abnormalCount", abnormalCount);
        result.put("ranges", NORMAL_RANGE);
        result.put("points", views);
        return result;
    }

    private void checkRange(String key, BigDecimal value, List<String> items) {
        if (value == null) {
            return;
        }
        BigDecimal[] range = NORMAL_RANGE.get(key);
        if (value.compareTo(range[0]) < 0) {
            items.add(LABEL.get(key) + "偏低");
        } else if (value.compareTo(range[1]) > 0) {
            items.add(LABEL.get(key) + "偏高");
        }
    }

    // ---------------- 规则 ----------------

    private void validateSeries(DrillParamSeries series) {
        if (series.getBoreholeId() == null || !boreholeRepository.existsById(series.getBoreholeId())) {
            throw new BizException("钻孔不存在，请选择有效钻孔");
        }
        if (series.getRecordDate() == null) {
            throw new BizException("记录日期不能为空");
        }
        if (!RigService.SHIFT_TYPE.contains(series.getShiftType())) {
            throw new BizException("班别只能为：早 / 中 / 夜");
        }
        List<ParamPoint> points = series.getPoints();
        if (points == null || points.size() < 2) {
            throw new BizException("采样点至少 2 个");
        }
        Set<String> times = new java.util.HashSet<>();
        for (int i = 0; i < points.size(); i++) {
            ParamPoint p = points.get(i);
            MineService.requireText(p.getTime(), "第 " + (i + 1) + " 个采样点的时刻不能为空");
            if (!times.add(p.getTime())) {
                throw new BizException("采样时刻重复：" + p.getTime());
            }
            checkLimit("pressure", p.getPressure(), i);
            checkLimit("rotation", p.getRotation(), i);
            checkLimit("pump", p.getPump(), i);
            checkLimit("mudDensity", p.getMudDensity(), i);
        }
    }

    private void checkLimit(String key, BigDecimal value, int index) {
        if (value == null) {
            throw new BizException("第 " + (index + 1) + " 个采样点的" + LABEL.get(key) + "不能为空");
        }
        BigDecimal[] limit = HARD_LIMIT.get(key);
        if (value.compareTo(limit[0]) < 0 || value.compareTo(limit[1]) > 0) {
            throw new BizException("第 " + (index + 1) + " 个采样点的" + LABEL.get(key) + " " + value
                    + " 超出允许范围 " + limit[0] + "~" + limit[1]);
        }
    }
}
