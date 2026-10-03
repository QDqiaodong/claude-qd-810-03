package com.drill.crew.domain;

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
 * 批次交接事件：封样发运 / 全部接收 / 部分接收 / 整批退回。
 * <p>
 * 只增不改：样品与批次的当前状态可能随后续处理变化，本表永久保留每次交接的结论、
 * 数量核对结果与操作痕迹，是「历史交接结果可追溯」的依据。
 */
@Entity
@Table(name = "sample_batch_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleBatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    /** PACK / RECEIVE / PARTIAL_RECEIVE / RETURN */
    @Column(name = "event_type", nullable = false, length = 18)
    private String eventType;

    /** 事件发生后批次状态 */
    @Column(name = "event_status", nullable = false, length = 18)
    private String eventStatus;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "received_count", nullable = false)
    private Integer receivedCount;

    @Column(name = "abnormal_count", nullable = false)
    private Integer abnormalCount;

    /** 操作人：封样人 / 接收人 */
    @Column(length = 32)
    private String operator;

    @Column(length = 255)
    private String note;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;
}
