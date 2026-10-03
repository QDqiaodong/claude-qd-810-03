package com.drill.crew.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钻进参数采样点：钻压 kN / 转速 rpm / 泵量 L/min / 泥浆密度 / 孔内情况备注。
 * 作为 JSON 数组元素持久化在 drill_param_series.points 列中。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParamPoint {

    /** 采样时刻，如 08:30 */
    private String time;

    /** 钻压 kN */
    private BigDecimal pressure;

    /** 转速 rpm */
    private BigDecimal rotation;

    /** 泵量 L/min */
    private BigDecimal pump;

    /** 泥浆密度 g/cm3 */
    private BigDecimal mudDensity;

    /** 孔内情况备注 */
    private String holeNote;
}
