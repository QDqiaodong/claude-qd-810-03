package com.drill.crew.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.HandoverDict;
import com.drill.crew.domain.CoreRound;
import com.drill.crew.domain.DrillParamSeries;
import com.drill.crew.domain.LithoLayer;
import com.drill.crew.domain.ParamPoint;
import com.drill.crew.domain.ShiftReport;
import com.drill.crew.repository.CoreRoundRepository;
import com.drill.crew.repository.DrillParamSeriesRepository;
import com.drill.crew.repository.LithoLayerRepository;
import com.drill.crew.repository.ShiftReportRepository;

/**
 * 交接资料检查引擎：按【当前实时资料】生成问题项。
 * <p>
 * 检查口径全部明确落为问题项：
 * <ol>
 *   <li>回次必须从 0 m 起、回次号连续、相邻回次首尾相接（不重叠、不留空）、末回次止于实际孔深；</li>
 *   <li>低采取率（&lt;75%）回次必须填写原因；</li>
 *   <li>每个回次内的岩性层必须连续、首尾相接、不重叠（与回次范围一致）；</li>
 *   <li>钻进参数时序中存在越出合理区间的异常点，要作为待处理项；</li>
 *   <li>班报缺少班组长签字，要作为待处理项。</li>
 * </ol>
 * 问题项的 issue_key 稳定：保存整改进度 / 重新提交时重算，key 消失即视为已处理。
 */
@Service
public class HandoverInspector {

    /** 相邻深度首尾相接允许的误差，与 CoreLogService 保持一致 */
    private static final BigDecimal DEPTH_TOLERANCE = new BigDecimal("0.01");

    private final CoreRoundRepository roundRepository;
    private final LithoLayerRepository layerRepository;
    private final DrillParamSeriesRepository seriesRepository;
    private final ShiftReportRepository reportRepository;

    public HandoverInspector(CoreRoundRepository roundRepository, LithoLayerRepository layerRepository,
                             DrillParamSeriesRepository seriesRepository, ShiftReportRepository reportRepository) {
        this.roundRepository = roundRepository;
        this.layerRepository = layerRepository;
        this.seriesRepository = seriesRepository;
        this.reportRepository = reportRepository;
    }

    /** 检查结果（保持稳定顺序，前端按类型分组） */
    public static class IssueSpec {
        public String issueType;
        public String targetType;
        public Long targetId;
        public String targetLabel;
        public String issueKey;
        public String message;

        IssueSpec(String issueType, String targetType, Long targetId, String targetLabel,
                  String issueKey, String message) {
            this.issueType = issueType;
            this.targetType = targetType;
            this.targetId = targetId;
            this.targetLabel = targetLabel;
            this.issueKey = issueKey;
            this.message = message;
        }
    }

    /**
     * 按实时资料检查一个钻孔，返回当前仍存在的问题项（未做任何持久化）。
     *
     * @param actualDepth 钻孔当前实际孔深
     */
    @Transactional(readOnly = true)
    public List<IssueSpec> inspect(Long boreholeId, BigDecimal actualDepth) {
        List<IssueSpec> issues = new ArrayList<>();
        inspectRounds(boreholeId, actualDepth, issues);
        inspectLayers(boreholeId, issues);
        inspectParams(boreholeId, issues);
        inspectReports(boreholeId, issues);
        return issues;
    }

    // ---------------- 回次 ----------------

    private void inspectRounds(Long boreholeId, BigDecimal actualDepth, List<IssueSpec> issues) {
        List<CoreRound> rounds = roundRepository.findByBoreholeIdOrderByRoundNoAsc(boreholeId);
        if (rounds.isEmpty()) {
            issues.add(new IssueSpec("ROUND_GAP", "round", null, "回次编录",
                    "ROUND_GAP:EMPTY", "该孔尚未录入任何回次，回次进尺从 0 m 起必须连续覆盖至终孔孔深"));
            return;
        }

        CoreRound first = rounds.get(0);
        if (first.getFromDepth().abs().compareTo(DEPTH_TOLERANCE) > 0) {
            issues.add(new IssueSpec("ROUND_GAP", "round", first.getId(),
                    "第 " + first.getRoundNo() + " 回次", "ROUND_GAP:START",
                    "回次未从 0.00 m 开始：首回次（第 " + first.getRoundNo() + " 回次）起深为 "
                            + first.getFromDepth() + " m，0 m 以浅存在空缺"));
        }

        CoreRound prev = null;
        for (CoreRound r : rounds) {
            // 回次号必须连续
            if (prev != null && r.getRoundNo() != prev.getRoundNo() + 1) {
                issues.add(new IssueSpec("ROUND_GAP", "round", r.getId(),
                        "第 " + r.getRoundNo() + " 回次", "ROUND_GAP:NO:" + prev.getRoundNo() + ":" + r.getRoundNo(),
                        "回次号不连续：第 " + prev.getRoundNo() + " 回次之后直接是第 " + r.getRoundNo() + " 回次"));
            }
            // 回次自身起止不能倒挂
            if (r.getToDepth().compareTo(r.getFromDepth()) <= 0) {
                issues.add(new IssueSpec("ROUND_GAP", "round", r.getId(),
                        "第 " + r.getRoundNo() + " 回次", "ROUND_GAP:INVERT:" + r.getRoundNo(),
                        "第 " + r.getRoundNo() + " 回次起止深度倒挂（" + r.getFromDepth() + " ~ "
                                + r.getToDepth() + " m），回次区间无效"));
            }
            // 与上一回次必须首尾相接：不能重叠、不能有空隙
            if (prev != null) {
                BigDecimal gap = r.getFromDepth().subtract(prev.getToDepth());
                if (gap.abs().compareTo(DEPTH_TOLERANCE) > 0) {
                    if (gap.signum() < 0) {
                        issues.add(new IssueSpec("ROUND_GAP", "round", r.getId(),
                                "第 " + r.getRoundNo() + " 回次",
                                "ROUND_GAP:OVERLAP:" + keyDepth(prev.getToDepth()) + ":" + keyDepth(r.getFromDepth()),
                                "回次深度重叠：第 " + prev.getRoundNo() + " 回次止深 " + prev.getToDepth()
                                        + " m，第 " + r.getRoundNo() + " 回次起深 " + r.getFromDepth() + " m"));
                    } else {
                        issues.add(new IssueSpec("ROUND_GAP", "round", r.getId(),
                                "第 " + r.getRoundNo() + " 回次",
                                "ROUND_GAP:VOID:" + keyDepth(prev.getToDepth()) + ":" + keyDepth(r.getFromDepth()),
                                "回次之间存在 " + gap.setScale(2, RoundingMode.HALF_UP) + " m 空缺：第 "
                                        + prev.getRoundNo() + " 回次止深 " + prev.getToDepth() + " m，第 "
                                        + r.getRoundNo() + " 回次起深 " + r.getFromDepth() + " m，必须连续"));
                    }
                }
            }
            // 低采取率必须有原因
            if (r.getRecoveryRate() != null
                    && r.getRecoveryRate().compareTo(CoreLogService.LOW_RECOVERY) < 0
                    && (r.getLowRecoveryReason() == null || r.getLowRecoveryReason().trim().isEmpty())) {
                issues.add(new IssueSpec("ROUND_LOW_RECOVERY", "round", r.getId(),
                        "第 " + r.getRoundNo() + " 回次", "ROUND_LOW_RECOVERY:" + r.getId(),
                        "第 " + r.getRoundNo() + " 回次采取率 " + r.getRecoveryRate() + "% 低于 "
                                + CoreLogService.LOW_RECOVERY + "%，必须填写低采取率原因（岩芯破碎 / 漏失 / 孔径失控）"));
            }
            prev = r;
        }

        // 末回次必须止于终孔实际孔深
        CoreRound last = rounds.get(rounds.size() - 1);
        if (actualDepth != null && last.getToDepth().subtract(actualDepth).abs().compareTo(DEPTH_TOLERANCE) > 0) {
            issues.add(new IssueSpec("ROUND_GAP", "round", last.getId(),
                    "第 " + last.getRoundNo() + " 回次",
                    "ROUND_GAP:END:" + keyDepth(last.getToDepth()) + ":" + keyDepth(actualDepth),
                    "末回次（第 " + last.getRoundNo() + " 回次）止深 " + last.getToDepth()
                            + " m 与实际孔深 " + actualDepth + " m 不一致，回次必须连续覆盖至孔底"));
        }
    }

    // ---------------- 岩性层 ----------------

    private void inspectLayers(Long boreholeId, List<IssueSpec> issues) {
        List<LithoLayer> layers = layerRepository.findByBoreholeIdOrderByFromDepthAsc(boreholeId);
        Map<Long, List<LithoLayer>> byRound = new LinkedHashMap<>();
        for (LithoLayer l : layers) {
            byRound.computeIfAbsent(l.getRoundId(), k -> new ArrayList<>()).add(l);
        }

        List<CoreRound> rounds = roundRepository.findByBoreholeIdOrderByRoundNoAsc(boreholeId);
        for (CoreRound round : rounds) {
            List<LithoLayer> siblings = byRound.get(round.getId());
            String roundLabel = "第 " + round.getRoundNo() + " 回次";
            if (siblings == null || siblings.isEmpty()) {
                issues.add(new IssueSpec("LAYER_GAP", "round", round.getId(), roundLabel,
                        "LAYER_GAP:EMPTY:" + round.getId(),
                        roundLabel + "（" + round.getFromDepth() + " ~ " + round.getToDepth()
                                + " m）未编录任何岩性层，回次范围内岩性分层不得空缺"));
                continue;
            }
            siblings.sort(Comparator.comparing(LithoLayer::getFromDepth).thenComparing(LithoLayer::getLayerSeq));

            LithoLayer first = siblings.get(0);
            if (first.getFromDepth().subtract(round.getFromDepth()).abs().compareTo(DEPTH_TOLERANCE) > 0) {
                issues.add(layerIssue(first, roundLabel,
                        "LAYER_GAP:HEAD:" + round.getId(),
                        roundLabel + "岩性层未从回次起始深度 " + round.getFromDepth() + " m 开始，首层起深为 "
                                + first.getFromDepth() + " m，存在空缺"));
            }

            LithoLayer prevLayer = null;
            for (LithoLayer layer : siblings) {
                if (layer.getToDepth().compareTo(layer.getFromDepth()) <= 0) {
                    issues.add(layerIssue(layer, roundLabel, "LAYER_GAP:INVERT:" + layer.getId(),
                            roundLabel + "第 " + layer.getLayerSeq() + " 层起止深度倒挂（"
                                    + layer.getFromDepth() + " ~ " + layer.getToDepth() + " m）"));
                }
                if (layer.getFromDepth().compareTo(round.getFromDepth()) < 0
                        || layer.getToDepth().compareTo(round.getToDepth()) > 0) {
                    issues.add(layerIssue(layer, roundLabel, "LAYER_GAP:OUT:" + layer.getId(),
                            roundLabel + "第 " + layer.getLayerSeq() + " 层深度 " + layer.getFromDepth() + " ~ "
                                    + layer.getToDepth() + " m 超出回次范围 " + round.getFromDepth() + " ~ "
                                    + round.getToDepth() + " m"));
                }
                if (prevLayer != null) {
                    BigDecimal gap = layer.getFromDepth().subtract(prevLayer.getToDepth());
                    if (gap.abs().compareTo(DEPTH_TOLERANCE) > 0) {
                        String tail;
                        String msg;
                        if (gap.signum() < 0) {
                            tail = "LAYER_GAP:OVERLAP:" + prevLayer.getId() + ":" + layer.getId();
                            msg = roundLabel + "岩性层重叠：第 " + prevLayer.getLayerSeq() + " 层止深 "
                                    + prevLayer.getToDepth() + " m，第 " + layer.getLayerSeq() + " 层起深 "
                                    + layer.getFromDepth() + " m，不允许重叠";
                        } else {
                            tail = "LAYER_GAP:VOID:" + prevLayer.getId() + ":" + layer.getId();
                            msg = roundLabel + "岩性层之间存在 " + gap.setScale(2, RoundingMode.HALF_UP)
                                    + " m 空缺：第 " + prevLayer.getLayerSeq() + " 层止深 " + prevLayer.getToDepth()
                                    + " m，第 " + layer.getLayerSeq() + " 层起深 " + layer.getFromDepth()
                                    + " m，必须连续";
                        }
                        issues.add(layerIssue(layer, roundLabel, tail, msg));
                    }
                }
                prevLayer = layer;
            }

            LithoLayer last = siblings.get(siblings.size() - 1);
            if (last.getToDepth().subtract(round.getToDepth()).abs().compareTo(DEPTH_TOLERANCE) > 0) {
                issues.add(layerIssue(last, roundLabel, "LAYER_GAP:TAIL:" + round.getId(),
                        roundLabel + "岩性层末层止深 " + last.getToDepth() + " m 与回次终止深度 "
                                + round.getToDepth() + " m 不一致，存在空缺"));
            }
        }
    }

    private IssueSpec layerIssue(LithoLayer layer, String roundLabel, String key, String message) {
        return new IssueSpec("LAYER_GAP", "layer", layer.getId(),
                roundLabel + " · 第 " + layer.getLayerSeq() + " 层", key, message);
    }

    // ---------------- 钻进参数 ----------------

    private void inspectParams(Long boreholeId, List<IssueSpec> issues) {
        List<DrillParamSeries> seriesList = seriesRepository.findByBoreholeIdOrderByRecordDateDesc(boreholeId);
        for (DrillParamSeries series : seriesList) {
            int abnormal = 0;
            List<String> firstItems = List.of();
            String firstTime = null;
            List<ParamPoint> points = series.getPoints() == null ? List.of() : series.getPoints();
            for (ParamPoint p : points) {
                List<String> items = new ArrayList<>();
                checkRange("pressure", p.getPressure(), items);
                checkRange("rotation", p.getRotation(), items);
                checkRange("pump", p.getPump(), items);
                checkRange("mudDensity", p.getMudDensity(), items);
                if (!items.isEmpty()) {
                    abnormal++;
                    if (firstTime == null) {
                        firstTime = p.getTime();
                        firstItems = items;
                    }
                }
            }
            if (abnormal > 0) {
                String label = series.getRecordDate() + " " + series.getShiftType() + "班参数";
                issues.add(new IssueSpec("PARAM_ABNORMAL", "series", series.getId(), label,
                        "PARAM_ABNORMAL:" + series.getId(),
                        label + "存在 " + abnormal + " 个越出合理区间的异常采样点（首个："
                                + (firstTime == null ? "" : firstTime + " ") + String.join("、", firstItems)
                                + "），需核实孔内情况并处理后才能接收"));
            }
        }
    }

    private void checkRange(String key, BigDecimal value, List<String> items) {
        if (value == null) {
            return;
        }
        BigDecimal[] range = DrillParamService.NORMAL_RANGE.get(key);
        if (value.compareTo(range[0]) < 0) {
            items.add(DrillParamService.LABEL.get(key) + "偏低");
        } else if (value.compareTo(range[1]) > 0) {
            items.add(DrillParamService.LABEL.get(key) + "偏高");
        }
    }

    // ---------------- 班报签字 ----------------

    private void inspectReports(Long boreholeId, List<IssueSpec> issues) {
        List<ShiftReport> reports = reportRepository.findByBoreholeIdOrderByReportDateDesc(boreholeId);
        for (ShiftReport r : reports) {
            if (r.getLeaderSign() == null || r.getLeaderSign().trim().isEmpty()) {
                String label = r.getReportDate() + " " + r.getShiftType() + "班班报";
                issues.add(new IssueSpec("REPORT_UNSIGNED", "report", r.getId(), label,
                        "REPORT_UNSIGNED:" + r.getId(),
                        label + "缺少班组长签字，班报不完整，补签字后才能接收"));
            }
        }
    }

    // ---------------- 工具 ----------------

    /** 深度拼进 issue_key 时统一两位小数，避免 347.2 / 347.20 造成 key 漂移 */
    private static String keyDepth(BigDecimal d) {
        return d.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 前端分组用的问题类型中文名 */
    public static String typeName(String type) {
        return switch (type) {
            case "ROUND_GAP" -> "回次不连续/重叠";
            case "ROUND_LOW_RECOVERY" -> "低采取率缺原因";
            case "LAYER_GAP" -> "岩性层不连续/重叠";
            case "PARAM_ABNORMAL" -> "钻进参数异常";
            case "REPORT_UNSIGNED" -> "班报缺少签字";
            default -> type;
        };
    }

    /** 问题项跳转页面：core / param / report */
    public static String targetPage(String targetType) {
        return switch (targetType) {
            case "series" -> HandoverDict.TARGET_PARAM;
            case "report" -> HandoverDict.TARGET_REPORT;
            default -> HandoverDict.TARGET_CORE;
        };
    }
}
