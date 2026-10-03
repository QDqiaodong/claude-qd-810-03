package com.drill.crew.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.drill.crew.domain.MaterialItem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 班报快照：与 {@link com.drill.crew.domain.ShiftReport} 字段对齐的纯 POJO，
 * 不带 JPA 的 rig 懒加载导航属性，可安全整体序列化进 handover.snapshot。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotReport {

    private Long id;
    private Long boreholeId;
    private Long rigId;
    private Long crewId;
    private LocalDate reportDate;
    private String shiftType;
    private BigDecimal footage;
    private BigDecimal pureHours;
    private BigDecimal auxHours;
    private String accidentNote;
    private List<MaterialItem> materials;
    private String handoverNote;
    private String leaderSign;
}
