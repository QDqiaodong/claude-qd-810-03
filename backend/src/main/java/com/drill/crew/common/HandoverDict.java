package com.drill.crew.common;

/**
 * 钻孔终孔交接的状态与问题项词典。
 * <ul>
 *   <li>PENDING_CHECK 待检查：班组刚发起 / 整改后重新提交，等地质负责人检查；</li>
 *   <li>NEED_FIX 待整改：负责人退回，班组只能保存整改进度，不能被确认接收；</li>
 *   <li>ACCEPTED 已接收：资料冻结，现有录入入口一律禁止增删改；</li>
 *   <li>SUPERSEDED 已失效：接收后返开，原接收版本保留并明确标记失效。</li>
 * </ul>
 */
public final class HandoverDict {

    private HandoverDict() {
    }

    public static final String PENDING_CHECK = "PENDING_CHECK";
    public static final String NEED_FIX = "NEED_FIX";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String SUPERSEDED = "SUPERSEDED";

    /** 问题项待处理 */
    public static final String ISSUE_OPEN = "OPEN";
    /** 问题项已处理（按当前资料重算后该问题已消除） */
    public static final String ISSUE_RESOLVED = "RESOLVED";

    /** 跳转目标：岩芯编录页（回次 / 岩性层） */
    public static final String TARGET_CORE = "core";
    /** 跳转目标：钻进参数页 */
    public static final String TARGET_PARAM = "param";
    /** 跳转目标：班报表页 */
    public static final String TARGET_REPORT = "report";

    public static String statusName(String code) {
        return switch (code) {
            case PENDING_CHECK -> "待检查";
            case NEED_FIX -> "待整改";
            case ACCEPTED -> "已接收";
            case SUPERSEDED -> "已失效";
            default -> code;
        };
    }
}
