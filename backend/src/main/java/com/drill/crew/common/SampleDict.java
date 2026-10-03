package com.drill.crew.common;

/**
 * 岩芯样品取样与送检链路词典。
 * <ul>
 *   <li>样品：DRAFT 草稿 → REGISTERED 已登记待送检 → IN_TRANSIT 运输中
 *       → RECEIVED 已接收 / ABNORMAL 部分接收时标出的待补样品；
 *       整批退回时随批样品回到 REGISTERED，可重新组批。</li>
 *   <li>批次：IN_TRANSIT 运输中 → RECEIVED 全部接收 / PARTIAL_RECEIVED 部分接收 / RETURNED 整批退回。</li>
 *   <li>批次明细行：PENDING 待接收 / RECEIVED 已接收 / ABNORMAL 异常待补 / RETURNED 随批退回。</li>
 * </ul>
 */
public final class SampleDict {

    private SampleDict() {
    }

    // ---------------- 样品状态 ----------------
    public static final String S_DRAFT = "DRAFT";
    public static final String S_REGISTERED = "REGISTERED";
    public static final String S_IN_TRANSIT = "IN_TRANSIT";
    public static final String S_RECEIVED = "RECEIVED";
    public static final String S_ABNORMAL = "ABNORMAL";

    // ---------------- 批次状态 ----------------
    public static final String B_IN_TRANSIT = "IN_TRANSIT";
    public static final String B_RECEIVED = "RECEIVED";
    public static final String B_PARTIAL = "PARTIAL_RECEIVED";
    public static final String B_RETURNED = "RETURNED";

    // ---------------- 批次明细行状态 ----------------
    public static final String ITEM_PENDING = "PENDING";
    public static final String ITEM_RECEIVED = "RECEIVED";
    public static final String ITEM_ABNORMAL = "ABNORMAL";
    public static final String ITEM_RETURNED = "RETURNED";

    // ---------------- 交接事件类型 ----------------
    public static final String EV_PACK = "PACK";
    public static final String EV_RECEIVE = "RECEIVE";
    public static final String EV_PARTIAL = "PARTIAL_RECEIVE";
    public static final String EV_RETURN = "RETURN";

    public static String sampleStatusName(String code) {
        return switch (code) {
            case S_DRAFT -> "草稿";
            case S_REGISTERED -> "已登记待送检";
            case S_IN_TRANSIT -> "运输中";
            case S_RECEIVED -> "已接收";
            case S_ABNORMAL -> "待补（接收异常）";
            default -> code;
        };
    }

    public static String batchStatusName(String code) {
        return switch (code) {
            case B_IN_TRANSIT -> "运输中";
            case B_RECEIVED -> "已接收";
            case B_PARTIAL -> "部分接收";
            case B_RETURNED -> "已退回";
            default -> code;
        };
    }

    public static String itemStatusName(String code) {
        return switch (code) {
            case ITEM_PENDING -> "待接收";
            case ITEM_RECEIVED -> "已接收";
            case ITEM_ABNORMAL -> "异常待补";
            case ITEM_RETURNED -> "随批退回";
            default -> code;
        };
    }

    public static String eventTypeName(String code) {
        return switch (code) {
            case EV_PACK -> "封样发运";
            case EV_RECEIVE -> "全部接收";
            case EV_PARTIAL -> "部分接收";
            case EV_RETURN -> "整批退回";
            default -> code;
        };
    }
}
