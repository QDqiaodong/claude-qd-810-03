package com.drill.crew.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * 钻进参数时序：一条时序 = 某孔某班按时间点记录的一组采样。
 * <p>
 * JSON 列用法 ②：points —— 自定义 POJO 列表（多段采样数组），
 * Hibernate 6 通过 @JdbcTypeCode(SqlTypes.JSON) 完成 List&lt;ParamPoint&gt; 的双向映射。
 */
@Entity
@Table(name = "drill_param_series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrillParamSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "rig_id")
    private Long rigId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "shift_type", nullable = false, length = 8)
    private String shiftType;

    @Column(length = 32)
    private String operator;

    /** JSON 列：多点采样数组 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "points", columnDefinition = "json")
    private List<ParamPoint> points;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
