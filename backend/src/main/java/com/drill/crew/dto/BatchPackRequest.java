package com.drill.crew.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 封样发运请求：现场选定若干【已登记待送检】样品组成一个送检批次。
 * 批次创建后样品定稿进入运输中，不可再增删。
 */
@Getter
@Setter
public class BatchPackRequest {

    /** 本次封样的样品 id 列表（明细与数量以此为准） */
    private List<Long> sampleIds;

    /** 封样人 */
    private String packer;

    /** 运输 / 送样人 */
    private String carrier;

    /** 封样运输说明 */
    private String note;
}
