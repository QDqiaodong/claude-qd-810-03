package com.drill.crew.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPQL 投影：按矿区统计孔数与累计进尺。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MineStatDTO {

    private Long mineId;
    private String mineName;
    private String stage;
    private Long holeCount;
    private BigDecimal totalFootage;

    /** 平均孔深（服务层回填） */
    private BigDecimal avgDepth;

    /** 该矿区最深孔深（服务层回填） */
    private BigDecimal maxDepth;

    /** JPQL 构造器投影专用（select new MineStatDTO(id, name, stage, holeCount, totalFootage)） */
    public MineStatDTO(Long mineId, String mineName, String stage, Long holeCount, BigDecimal totalFootage) {
        this.mineId = mineId;
        this.mineName = mineName;
        this.stage = stage;
        this.holeCount = holeCount;
        this.totalFootage = totalFootage;
    }
}
