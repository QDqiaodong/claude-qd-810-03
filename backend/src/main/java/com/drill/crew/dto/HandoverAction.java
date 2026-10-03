package com.drill.crew.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 交接推进操作请求：发起 / 保存整改进度 / 重新提交 / 退回 / 接收 / 返开。
 * expectedVersion 为页面上看到的 lockVersion，过期则返回 409 并发冲突。
 */
@Getter
@Setter
public class HandoverAction {

    /** 乐观锁版本（发起时可不传） */
    private Integer expectedVersion;

    /** 操作人：班组发起人为班组长；退回 / 接收 / 返开时为地质负责人 */
    private String operator;

    /** 班组提交说明（发起 / 重新提交） */
    private String note;

    /** 退回原因（退回时必填）或返开原因（返开时必填） */
    private String reason;
}
