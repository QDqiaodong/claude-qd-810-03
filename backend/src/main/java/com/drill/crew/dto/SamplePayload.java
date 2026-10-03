package com.drill.crew.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 取样登记保存请求（新增 / 修改共用）。
 * <p>
 * 是否成为正式样品由后端按关键信息是否齐全判定：样品编号、起止深度、取样人、封签号齐全 → REGISTERED；
 * 缺任一项 → DRAFT 草稿。前端不直接决定状态，避免「信息不全也强行登记」。
 */
@Getter
@Setter
public class SamplePayload {

    private Long boreholeId;

    private String sampleNo;

    private BigDecimal fromDepth;

    private BigDecimal toDepth;

    /** 取样人 */
    private String sampler;

    private String sealNo;

    /** 样品类型：岩芯 / 岩屑 / 水样 / 矿化样 */
    private String sampleType;

    private String remark;

    /** 页面上看到的 lockVersion，过期返回 409 */
    private Integer expectedVersion;
}
