package com.drill.crew.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPQL 投影：按岩性统计层数、累计厚度与厚度占比。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LithoStatDTO {

    private String lithology;
    private Long layerCount;
    private BigDecimal totalThickness;

    /** 厚度占比 %（服务层回填） */
    private BigDecimal ratio;

    /** JPQL 构造器投影专用（select new LithoStatDTO(lithology, layerCount, totalThickness)） */
    public LithoStatDTO(String lithology, Long layerCount, BigDecimal totalThickness) {
        this.lithology = lithology;
        this.layerCount = layerCount;
        this.totalThickness = totalThickness;
    }
}
