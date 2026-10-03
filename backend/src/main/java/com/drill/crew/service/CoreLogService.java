package com.drill.crew.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CoreRound;
import com.drill.crew.domain.LithoLayer;
import com.drill.crew.dto.LithoStatDTO;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CoreRoundRepository;
import com.drill.crew.repository.LithoLayerRepository;

/**
 * 岩芯编录 —— 本仓核心业务：回次 + 岩性分层（分层 attrs 为 JSON 属性包）。
 */
@Service
public class CoreLogService {

    /** 低采取率阈值：低于该值必须填写原因 */
    public static final BigDecimal LOW_RECOVERY = new BigDecimal("75");
    /** 低采取率原因取值 */
    public static final Set<String> LOW_RECOVERY_REASONS = Set.of("岩芯破碎", "漏失", "孔径失控");
    /** 采取率与「岩芯长度 / 进尺」允许的计算误差 */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");
    /** 分层深度首尾相接允许的误差 */
    private static final BigDecimal DEPTH_TOLERANCE = new BigDecimal("0.01");

    private final CoreRoundRepository roundRepository;
    private final LithoLayerRepository layerRepository;
    private final BoreholeRepository boreholeRepository;
    private final HandoverLockService handoverLock;

    public CoreLogService(CoreRoundRepository roundRepository, LithoLayerRepository layerRepository,
                          BoreholeRepository boreholeRepository, HandoverLockService handoverLock) {
        this.roundRepository = roundRepository;
        this.layerRepository = layerRepository;
        this.boreholeRepository = boreholeRepository;
        this.handoverLock = handoverLock;
    }

    // ---------------- 回次 ----------------

    @Transactional(readOnly = true)
    public List<CoreRound> listRounds(Long boreholeId) {
        if (boreholeId == null) {
            return roundRepository.findAll();
        }
        return roundRepository.findByBoreholeIdOrderByRoundNoAsc(boreholeId);
    }

    @Transactional(readOnly = true)
    public CoreRound getRound(Long id) {
        return roundRepository.findById(id).orElseThrow(() -> new BizException("回次不存在：id=" + id));
    }

    @Transactional
    public CoreRound createRound(CoreRound round) {
        validateRound(round, true);
        round.setId(null);
        return roundRepository.save(round);
    }

    @Transactional
    public CoreRound updateRound(Long id, CoreRound payload) {
        CoreRound round = getRound(id);
        if (payload.getFromDepth() != null) {
            round.setFromDepth(payload.getFromDepth());
        }
        if (payload.getToDepth() != null) {
            round.setToDepth(payload.getToDepth());
        }
        if (payload.getCoreLength() != null) {
            round.setCoreLength(payload.getCoreLength());
        }
        if (payload.getRecoveryRate() != null) {
            round.setRecoveryRate(payload.getRecoveryRate());
        }
        if (payload.getRqd() != null) {
            round.setRqd(payload.getRqd());
        }
        if (payload.getDriller() != null) {
            round.setDriller(payload.getDriller());
        }
        if (payload.getSampledAt() != null) {
            round.setSampledAt(payload.getSampledAt());
        }
        if (payload.getLowRecoveryReason() != null) {
            round.setLowRecoveryReason(payload.getLowRecoveryReason());
        }
        if (payload.getRemark() != null) {
            round.setRemark(payload.getRemark());
        }
        validateRound(round, false);
        return roundRepository.save(round);
    }

    @Transactional
    public void deleteRound(Long id) {
        CoreRound round = getRound(id);
        handoverLock.requireWritable(round.getBoreholeId(), "删除回次");
        if (!layerRepository.findByRoundIdOrderByLayerSeqAsc(id).isEmpty()) {
            throw new BizException("该回次下已有岩性分层，请先删除分层");
        }
        roundRepository.delete(round);
    }

    // ---------------- 岩性分层 ----------------

    @Transactional(readOnly = true)
    public List<LithoLayer> listLayers(Long boreholeId, Long roundId) {
        if (roundId != null) {
            return layerRepository.findByRoundIdOrderByLayerSeqAsc(roundId);
        }
        if (boreholeId != null) {
            return layerRepository.findByBoreholeIdOrderByFromDepthAsc(boreholeId);
        }
        return layerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public LithoLayer getLayer(Long id) {
        return layerRepository.findById(id).orElseThrow(() -> new BizException("岩性分层不存在：id=" + id));
    }

    @Transactional
    public LithoLayer createLayer(LithoLayer layer) {
        validateLayer(layer, true);
        layer.setId(null);
        return layerRepository.save(layer);
    }

    @Transactional
    public LithoLayer updateLayer(Long id, LithoLayer payload) {
        LithoLayer layer = getLayer(id);
        if (payload.getLayerSeq() != null) {
            layer.setLayerSeq(payload.getLayerSeq());
        }
        if (payload.getLithology() != null) {
            layer.setLithology(payload.getLithology());
        }
        if (payload.getFromDepth() != null) {
            layer.setFromDepth(payload.getFromDepth());
        }
        if (payload.getToDepth() != null) {
            layer.setToDepth(payload.getToDepth());
        }
        if (payload.getColor() != null) {
            layer.setColor(payload.getColor());
        }
        if (payload.getWeathering() != null) {
            layer.setWeathering(payload.getWeathering());
        }
        if (payload.getStructure() != null) {
            layer.setStructure(payload.getStructure());
        }
        if (payload.getDescription() != null) {
            layer.setDescription(payload.getDescription());
        }
        if (payload.getAttrs() != null) {
            layer.setAttrs(payload.getAttrs());
        }
        validateLayer(layer, false);
        return layerRepository.save(layer);
    }

    /**
     * 单独更新岩芯分层的 JSON 属性包（含水性 / 硬度 / 裂隙发育 / 矿化 / 蚀变 / 取样编号 …）。
     */
    @Transactional
    public LithoLayer updateAttrs(Long id, Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            throw new BizException("属性包不能为空");
        }
        Map<String, Object> cleaned = new LinkedHashMap<>();
        attrs.forEach((k, v) -> {
            if (k == null || k.trim().isEmpty()) {
                throw new BizException("属性名不能为空");
            }
            cleaned.put(k.trim(), v);
        });
        LithoLayer layer = getLayer(id);
        handoverLock.requireWritable(layer.getBoreholeId(), "修改岩性层属性包");
        layer.setAttrs(cleaned);
        return layerRepository.save(layer);
    }

    @Transactional
    public void deleteLayer(Long id) {
        LithoLayer layer = getLayer(id);
        handoverLock.requireWritable(layer.getBoreholeId(), "删除岩性层");
        layerRepository.delete(layer);
    }

    // ---------------- 统计 ----------------

    /**
     * 手写 JPQL ②的结果：按岩性统计厚度占比。
     */
    @Transactional(readOnly = true)
    public List<LithoStatDTO> lithologyStats() {
        List<LithoStatDTO> list = layerRepository.statThicknessByLithology();
        BigDecimal total = list.stream()
                .map(s -> MineService.nvl(s.getTotalThickness()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (LithoStatDTO s : list) {
            BigDecimal thickness = MineService.nvl(s.getTotalThickness());
            s.setRatio(total.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                    : thickness.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP));
        }
        return list;
    }

    /** 低采取率回次清单（编录质量核查） */
    @Transactional(readOnly = true)
    public List<CoreRound> lowRecoveryRounds() {
        return roundRepository.findByRecoveryRateLessThanOrderByRecoveryRateAsc(LOW_RECOVERY);
    }

    // ---------------- 业务规则 ----------------

    private void validateRound(CoreRound round, boolean isCreate) {
        if (round.getBoreholeId() == null) {
            throw new BizException("回次必须挂在某个钻孔下");
        }
        Borehole hole = boreholeRepository.findById(round.getBoreholeId())
                .orElseThrow(() -> new BizException("钻孔不存在：id=" + round.getBoreholeId()));
        if ("封孔".equals(hole.getStatus())) {
            throw new BizException("钻孔【" + hole.getHoleNo() + "】已封孔，不允许再录入回次");
        }
        handoverLock.requireWritable(round.getBoreholeId(), "录入或修改回次");
        if (round.getRoundNo() == null || round.getRoundNo() <= 0) {
            throw new BizException("回次号必须为正整数");
        }
        if (isCreate && roundRepository.existsByBoreholeIdAndRoundNo(round.getBoreholeId(), round.getRoundNo())) {
            throw new BizException("孔【" + hole.getHoleNo() + "】的第 " + round.getRoundNo() + " 回次已存在");
        }
        if (round.getFromDepth() == null || round.getToDepth() == null) {
            throw new BizException("回次起止深度不能为空");
        }
        if (round.getFromDepth().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("回次起始深度不能为负数");
        }
        if (round.getToDepth().compareTo(round.getFromDepth()) <= 0) {
            throw new BizException("回次起止深度倒挂：起始 " + round.getFromDepth() + " m 不得大于或等于终止 "
                    + round.getToDepth() + " m");
        }
        if (round.getToDepth().compareTo(hole.getActualDepth()) > 0) {
            throw new BizException("回次终止深度 " + round.getToDepth() + " m 超过当前孔深 " + hole.getActualDepth() + " m");
        }
        BigDecimal interval = round.getToDepth().subtract(round.getFromDepth());
        if (round.getCoreLength() == null || round.getCoreLength().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("岩芯长度必须大于 0");
        }
        if (round.getCoreLength().compareTo(interval) > 0) {
            throw new BizException("岩芯长度 " + round.getCoreLength() + " m 不得大于回次进尺 " + interval + " m");
        }

        BigDecimal expected = round.getCoreLength()
                .multiply(BigDecimal.valueOf(100))
                .divide(interval, 2, RoundingMode.HALF_UP);
        if (round.getRecoveryRate() == null) {
            round.setRecoveryRate(expected);
        } else if (round.getRecoveryRate().subtract(expected).abs().compareTo(TOLERANCE) > 0) {
            throw new BizException("采取率与岩芯长度不匹配，按进尺 " + interval + " m 计算应为 " + expected + "%");
        }
        if (round.getRqd() == null) {
            throw new BizException("RQD 不能为空");
        }
        if (round.getRqd().compareTo(round.getRecoveryRate()) > 0) {
            throw new BizException("RQD " + round.getRqd() + "% 不得大于采取率 " + round.getRecoveryRate() + "%");
        }
        if (round.getRecoveryRate().compareTo(LOW_RECOVERY) < 0) {
            if (round.getLowRecoveryReason() == null || round.getLowRecoveryReason().trim().isEmpty()) {
                throw new BizException("采取率 " + round.getRecoveryRate() + "% 低于 " + LOW_RECOVERY + "%，必须填写原因");
            }
            if (!LOW_RECOVERY_REASONS.contains(round.getLowRecoveryReason().trim())) {
                throw new BizException("低采取率原因只能为：岩芯破碎 / 漏失 / 孔径失控");
            }
        }
    }

    private void validateLayer(LithoLayer layer, boolean isCreate) {
        if (layer.getRoundId() == null) {
            throw new BizException("岩性分层必须挂在某个回次下");
        }
        CoreRound round = roundRepository.findById(layer.getRoundId())
                .orElseThrow(() -> new BizException("回次不存在：id=" + layer.getRoundId()));
        layer.setBoreholeId(round.getBoreholeId());
        Borehole hole = boreholeRepository.findById(round.getBoreholeId()).orElse(null);
        if (hole != null && "封孔".equals(hole.getStatus())) {
            throw new BizException("钻孔【" + hole.getHoleNo() + "】已封孔，不允许再录入岩性分层");
        }
        handoverLock.requireWritable(round.getBoreholeId(), "录入或修改岩性层");
        MineService.requireText(layer.getLithology(), "岩性名称不能为空");
        if (layer.getLayerSeq() == null || layer.getLayerSeq() <= 0) {
            throw new BizException("层序必须为正整数");
        }
        if (isCreate && layerRepository.existsByRoundIdAndLayerSeq(layer.getRoundId(), layer.getLayerSeq())) {
            throw new BizException("该回次下第 " + layer.getLayerSeq() + " 层已存在");
        }
        if (layer.getFromDepth() == null || layer.getToDepth() == null) {
            throw new BizException("岩性分层起止深度不能为空");
        }
        if (layer.getToDepth().compareTo(layer.getFromDepth()) <= 0) {
            throw new BizException("岩性分层起止深度倒挂：起始 " + layer.getFromDepth() + " m 不得大于或等于终止 "
                    + layer.getToDepth() + " m");
        }
        if (layer.getFromDepth().compareTo(round.getFromDepth()) < 0 || layer.getToDepth().compareTo(round.getToDepth()) > 0) {
            throw new BizException("岩性分层深度 " + layer.getFromDepth() + "~" + layer.getToDepth()
                    + " m 必须落在回次 " + round.getFromDepth() + "~" + round.getToDepth() + " m 范围内");
        }

        // 相邻岩性分层必须首尾相接：不能重叠、不能有空隙
        List<LithoLayer> siblings = new ArrayList<>(layerRepository.findByRoundIdOrderByLayerSeqAsc(layer.getRoundId()));
        if (!isCreate) {
            siblings.removeIf(s -> s.getId().equals(layer.getId()));
        }
        siblings.add(layer);
        siblings.sort(Comparator.comparing(LithoLayer::getFromDepth).thenComparing(LithoLayer::getLayerSeq));
        for (int i = 1; i < siblings.size(); i++) {
            LithoLayer prev = siblings.get(i - 1);
            LithoLayer curr = siblings.get(i);
            if (curr.getFromDepth().subtract(prev.getToDepth()).abs().compareTo(DEPTH_TOLERANCE) > 0) {
                throw new BizException("相邻岩性分层深度必须首尾相接：上一段止深 " + prev.getToDepth() + " m 与下一段起深 "
                        + curr.getFromDepth() + " m 之间存在重叠或空隙");
            }
        }
        LithoLayer first = siblings.get(0);
        LithoLayer last = siblings.get(siblings.size() - 1);
        if (first.getFromDepth().subtract(round.getFromDepth()).abs().compareTo(DEPTH_TOLERANCE) > 0) {
            throw new BizException("分层须自回次起始深度 " + round.getFromDepth() + " m 开始，当前首层起深为 "
                    + first.getFromDepth() + " m，存在空隙");
        }
        if (last.getToDepth().subtract(round.getToDepth()).abs().compareTo(DEPTH_TOLERANCE) > 0) {
            throw new BizException("分层须止于回次终止深度 " + round.getToDepth() + " m，当前末层止深为 "
                    + last.getToDepth() + " m，存在空隙");
        }
    }
}
