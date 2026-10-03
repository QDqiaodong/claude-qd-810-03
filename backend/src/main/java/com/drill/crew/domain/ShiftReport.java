package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钻探班报表：每班一张，含进尺、纯钻/辅助时间、事故停待、材料消耗、交接班备注。
 * <p>
 * JSON 列用法 ③：materials —— 材料消耗明细（自定义 POJO 列表）。
 */
@Entity
@Table(name = "shift_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "rig_id", nullable = false)
    private Long rigId;

    @Column(name = "crew_id")
    private Long crewId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "shift_type", nullable = false, length = 8)
    private String shiftType;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal footage;

    @Column(name = "pure_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal pureHours;

    @Column(name = "aux_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal auxHours;

    @Column(name = "accident_note", length = 255)
    private String accidentNote;

    /** JSON 列：材料消耗明细 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "materials", columnDefinition = "json")
    private List<MaterialItem> materials;

    @Column(name = "handover_note", length = 255)
    private String handoverNote;

    @Column(name = "leader_sign", length = 32)
    private String leaderSign;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 只读导航属性，供 JPQL 按钻机统计台月效率使用 */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rig_id", insertable = false, updatable = false)
    private Rig rig;
}
