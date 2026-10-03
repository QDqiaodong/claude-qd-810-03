package com.drill.crew.common;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钻探词典与业务规则参数表（孔状态、岩性、钻机状态、班别、钻进参数合理区间等）。
 * 规则集中在此，便于出题时被要求调整或扩展。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 钻孔状态 ----------------
    private static final Map<String, String> HOLE_STATUS = Map.of(
            "DRILLING", "施工",
            "FINISHED", "终孔",
            "SEALED", "封孔");

    // ---------------- 钻机状态 ----------------
    private static final Map<String, String> RIG_STATUS = Map.of(
            "RUNNING", "运行",
            "MOVING", "搬迁",
            "REPAIR", "维修",
            "IDLE", "停用");

    // ---------------- 班别 ----------------
    private static final List<String> SHIFT_ORDER = List.of("早", "中", "夜");

    // ---------------- 岩性与代表色（编录图例） ----------------
    private static final Map<String, String> LITHOLOGY_COLOR = Map.of(
            "第四系覆盖层", "#c8a165",
            "泥岩", "#6d4c41",
            "砂岩", "#8d6e63",
            "灰岩", "#bcaaa4",
            "花岗岩", "#5d4037",
            "石英脉", "#a1887f");

    /** 钻探方法（随孔深与地层变化） */
    private static final Map<String, String> DRILL_METHOD = Map.of(
            "CORE", "取芯钻进",
            "FULL", "全面钻进",
            "RC", "反循环");

    // ---------------- 业务规则参数 ----------------

    /** 采取率预警线（低于此值必须填写原因） */
    public static final BigDecimal RECOVERY_WARN_LINE = new BigDecimal("75.00");

    /** 单回次最大允许进尺 m */
    public static final BigDecimal MAX_ROUND_LENGTH = new BigDecimal("9.00");

    /** 班次时长上限 h（纯钻 + 辅助不得超时） */
    public static final BigDecimal SHIFT_HOUR_LIMIT = new BigDecimal("8.00");

    /** 台月效率折算：每月按 30 班、每班 8 小时 */
    public static final BigDecimal MONTH_SHIFT_COUNT = new BigDecimal("30");

    /** 钻进参数合理区间：钻压 kN / 转速 rpm / 泵量 L/min / 泥浆密度 g/cm3 */
    private static final Map<String, BigDecimal[]> NORMAL_RANGE = new HashMap<>();

    static {
        NORMAL_RANGE.put("PRESSURE", new BigDecimal[] { new BigDecimal("4"), new BigDecimal("18") });
        NORMAL_RANGE.put("ROTATION", new BigDecimal[] { new BigDecimal("60"), new BigDecimal("320") });
        NORMAL_RANGE.put("PUMP", new BigDecimal[] { new BigDecimal("80"), new BigDecimal("260") });
        NORMAL_RANGE.put("MUD_DENSITY", new BigDecimal[] { new BigDecimal("1.05"), new BigDecimal("1.25") });
    }

    public static String holeStatusName(String code) {
        return HOLE_STATUS.getOrDefault(code, code);
    }

    public static String rigStatusName(String code) {
        return RIG_STATUS.getOrDefault(code, code);
    }

    public static String lithologyColor(String lithology) {
        return LITHOLOGY_COLOR.getOrDefault(lithology, "#8d6e63");
    }

    public static BigDecimal[] normalRange(String key) {
        return NORMAL_RANGE.get(key);
    }

    public static List<String> shiftOrder() {
        return SHIFT_ORDER;
    }

    public static Map<String, String> drillMethods() {
        return DRILL_METHOD;
    }
}
