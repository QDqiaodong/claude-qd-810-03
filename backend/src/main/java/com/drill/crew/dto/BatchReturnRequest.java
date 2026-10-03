package com.drill.crew.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 整批退回请求：实验室在接收核对前发现整批不符（如送样单缺失、来源无法核对）时退回。
 * 退回原因必填，会随批次与交接事件永久留痕。
 */
@Getter
@Setter
public class BatchReturnRequest {

    /** 实验室经办人 */
    private String receiver;

    /** 退回原因（必填） */
    private String reason;

    /** 页面上看到的批次 lockVersion，过期返回 409 */
    private Integer expectedVersion;
}
