package com.drill.crew.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPQL 投影：按钻机统计台月效率（纯钻时间 / 台班数 / 有效台月率）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RigEfficiencyDTO {

    private Long rigId;
    private String rigCode;
    private String rigModel;
    private String rigStatus;
    private Long shiftCount;
    private BigDecimal totalFootage;
    private BigDecimal pureHours;
    private BigDecimal auxHours;

    /** 台班平均进尺 m/班 */
    private BigDecimal footagePerShift;

    /** 纯钻时间占比 %（纯钻 / (纯钻 + 辅助)） */
    private BigDecimal pureRatio;

    /** 折算台月效率 m/台月（按 30 班 × 8 h 折算） */
    private BigDecimal meterPerMonth;

    /** JPQL 构造器投影专用（select new RigEfficiencyDTO(rigId, code, model, status, shiftCount, footage, pure, aux)） */
    public RigEfficiencyDTO(Long rigId, String rigCode, String rigModel, String rigStatus,
                            Long shiftCount, BigDecimal totalFootage,
                            BigDecimal pureHours, BigDecimal auxHours) {
        this.rigId = rigId;
        this.rigCode = rigCode;
        this.rigModel = rigModel;
        this.rigStatus = rigStatus;
        this.shiftCount = shiftCount;
        this.totalFootage = totalFootage;
        this.pureHours = pureHours;
        this.auxHours = auxHours;
    }
}
