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
 * 岩芯样品（现场取样登记）。
 * <p>
 * 关键信息：样品编号 {@code sampleNo}、起止深度、取样人 {@code sampler}、封签号 {@code sealNo}。
 * 四项齐全才能登记为 {@link com.drill.crew.common.SampleDict#S_REGISTERED}；
 * 缺任一项只能保存 {@link com.drill.crew.common.SampleDict#S_DRAFT} 草稿，
 * 草稿不参与同孔孔段重叠校验、不能加入送检批次。
 * <p>
 * sample_no / seal_no 由唯一索引兜底（草稿为空，MySQL 唯一索引中多个 NULL 不冲突）；
 * 同孔正式样品孔段不得重叠，由 SampleService 在保存时校验。
 * {@code lockVersion} 为手工乐观锁，防止重复点击保存产生覆盖/重复。
 */
@Entity
@Table(name = "core_sample")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoreSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    /** 样品编号：草稿可空，正式样品必填且全局唯一 */
    @Column(name = "sample_no", length = 40)
    private String sampleNo;

    @Column(name = "from_depth", precision = 8, scale = 2)
    private BigDecimal fromDepth;

    @Column(name = "to_depth", precision = 8, scale = 2)
    private BigDecimal toDepth;

    /** 取样人 */
    @Column(length = 32)
    private String sampler;

    /** 封签号：草稿可空，正式样品必填且全局唯一 */
    @Column(name = "seal_no", length = 40)
    private String sealNo;

    @Column(name = "sample_type", length = 24)
    private String sampleType;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(length = 255)
    private String remark;

    /** 当前所在批次；整批退回后清空（可重新组批），历史去向在 sample_batch_event 可追溯 */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;
}
