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
 * 送检批次（封样运输 / 实验室接收）。
 * <p>
 * 创建批次即「封样发运」（{@link com.drill.crew.common.SampleDict#B_IN_TRANSIT}），
 * 创建后批次样品定稿、不可增删。{@code expectedCount} 为封样数量，
 * 实验室接收时必须与实到数量逐行核对；封签号同样逐行核对。
 * <ul>
 *   <li>全部一致 → RECEIVED；</li>
 *   <li>部分异常 → PARTIAL_RECEIVED：一致样品照样接收并计数，异常样品标 ABNORMAL 待补；</li>
 *   <li>整批退回 → RETURNED：样品退回 REGISTERED 可重新组批。</li>
 * </ul>
 * {@code lockVersion} 乐观锁 + 状态机校验，保证重复点击交接/接收不会产生重复记录。
 */
@Entity
@Table(name = "sample_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次编号：SJ-yyyyMMdd-序号，自动生成、全局唯一 */
    @Column(name = "batch_no", nullable = false, length = 40)
    private String batchNo;

    @Column(nullable = false, length = 18)
    private String status;

    /** 封样时样品数量（接收核对基准） */
    @Column(name = "expected_count", nullable = false)
    private Integer expectedCount;

    @Column(name = "received_count", nullable = false)
    private Integer receivedCount;

    @Column(name = "abnormal_count", nullable = false)
    private Integer abnormalCount;

    /** 封样人 */
    @Column(length = 32)
    private String packer;

    /** 运输 / 送样人 */
    @Column(length = 32)
    private String carrier;

    @Column(name = "pack_note", length = 255)
    private String packNote;

    @Column(name = "packed_at")
    private LocalDateTime packedAt;

    /** 实验室接收人 */
    @Column(length = 32)
    private String receiver;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** 接收总说明 / 异常说明（回写） */
    @Column(name = "receive_note", length = 255)
    private String receiveNote;

    /** 整批退回原因 */
    @Column(name = "return_reason", length = 255)
    private String returnReason;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
