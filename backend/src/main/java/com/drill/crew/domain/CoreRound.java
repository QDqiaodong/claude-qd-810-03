package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * 钻进回次：起止深度、岩芯长度、采取率、RQD。
 * 采取率低于 75% 必须填写 lowRecoveryReason（岩芯破碎 / 漏失 / 孔径失控）。
 */
@Entity
@Table(name = "core_round")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoreRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "from_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal fromDepth;

    @Column(name = "to_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal toDepth;

    @Column(name = "core_length", nullable = false, precision = 8, scale = 2)
    private BigDecimal coreLength;

    @Column(name = "recovery_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal recoveryRate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rqd;

    @Column(length = 32)
    private String driller;

    @Column(name = "sampled_at")
    private LocalDate sampledAt;

    @Column(name = "low_recovery_reason", length = 32)
    private String lowRecoveryReason;

    @Column(length = 255)
    private String remark;
}
