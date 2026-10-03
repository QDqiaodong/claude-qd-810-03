package com.drill.crew.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 实验室接收请求：按批次明细逐样核对数量与封签后回写。
 * <ul>
 *   <li>每行必须覆盖批次中的一个样品（数量核对：行数 = 封样数量）；</li>
 *   <li>abnormal=false：数量、封签一致，正常接收；</li>
 *   <li>abnormal=true：数量不符 / 封签破损 / 封签不符等待补项，必须填异常说明。</li>
 * </ul>
 * 全部正常 → 批次 RECEIVED；存在异常 → PARTIAL_RECEIVED（已接收样品保留）。
 */
@Getter
@Setter
public class BatchReceiveRequest {

    private String receiver;

    /** 接收总说明 / 异常概述 */
    private String note;

    private List<Line> items;

    /** 页面上看到的批次 lockVersion，过期返回 409 */
    private Integer expectedVersion;

    @Getter
    @Setter
    public static class Line {
        /** 批次明细行对应的样品 id */
        private Long sampleId;

        /** 是否异常待补 */
        private boolean abnormal;

        /** 异常说明（abnormal=true 时必填） */
        private String abnormalNote;
    }
}
