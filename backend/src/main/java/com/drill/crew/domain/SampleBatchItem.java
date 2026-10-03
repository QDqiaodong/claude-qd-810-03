package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批次样品明细行：封样时定稿的样品快照。
 * <p>
 * 封样时记录样品编号、孔段、封签号；实验室接收时逐行核对并回写行状态、行级异常说明与接收时间。
 * 部分接收时已接收行保持 RECEIVED（保留已接收样品），异常行标 ABNORMAL（待补项）。
 * (batch_id, sample_id) 批次内唯一：整批退回后样品可在新批次重新产生明细行，旧批次退回明细留痕。
 */
@Entity
@Table(name = "sample_batch_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId;

    @Column(name = "sample_no", nullable = false, length = 40)
    private String sampleNo;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "from_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal fromDepth;

    @Column(name = "to_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal toDepth;

    /** 封签号快照：实验室逐行核对的依据 */
    @Column(name = "seal_no", nullable = false, length = 40)
    private String sealNo;

    @Column(name = "item_status", nullable = false, length = 10)
    private String itemStatus;

    /** 行级异常说明：数量不符 / 封签破损 / 封签不符 / 样品状态异常 */
    @Column(name = "abnormal_note", length = 255)
    private String abnormalNote;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(length = 32)
    private String receiver;
}
