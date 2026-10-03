package com.drill.crew.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 班报材料消耗明细：类别（钻头 / 泥浆材料）、名称、规格、数量、单位。
 * 作为 JSON 数组元素持久化在 shift_report.materials 列中。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialItem {

    /** 类别：钻头 / 泥浆材料 */
    private String category;

    private String name;

    private String spec;

    private BigDecimal qty;

    private String unit;
}
