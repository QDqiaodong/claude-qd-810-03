package com.drill.crew.dto;

import java.math.BigDecimal;
import java.util.List;

import com.drill.crew.domain.ParamPoint;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钻进参数采样点的分析视图：在原 JSON 采样点上叠加异常判定结果。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParamPointView {

    private int index;
    private String time;
    private BigDecimal pressure;
    private BigDecimal rotation;
    private BigDecimal pump;
    private BigDecimal mudDensity;
    private String holeNote;

    /** 是否超出合理区间 */
    private boolean abnormal;

    /** 越限项说明，如 ["钻压偏高","泥浆密度偏高"] */
    private List<String> abnormalItems;

    public static ParamPointView of(int index, ParamPoint p) {
        ParamPointView v = new ParamPointView();
        v.setIndex(index);
        v.setTime(p.getTime());
        v.setPressure(p.getPressure());
        v.setRotation(p.getRotation());
        v.setPump(p.getPump());
        v.setMudDensity(p.getMudDensity());
        v.setHoleNote(p.getHoleNote());
        v.setAbnormal(false);
        v.setAbnormalItems(List.of());
        return v;
    }
}
