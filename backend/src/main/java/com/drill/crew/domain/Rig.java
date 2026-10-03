package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

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
 * 钻机。状态：运行 / 搬迁 / 维修 / 停用。
 * <p>
 * JSON 列用法 ④：specs —— 动力形式、卷扬提升力、泥浆泵型号等随型号变化的可变参数。
 */
@Entity
@Table(name = "rig")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String model;

    @Column(name = "max_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxDepth;

    @Column(name = "engine_power", nullable = false, precision = 8, scale = 2)
    private BigDecimal enginePower;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 64)
    private String manufacturer;

    @Column(name = "commissioned_on")
    private LocalDate commissionedOn;

    /** JSON 列：钻机可变技术参数包 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specs", columnDefinition = "json")
    private Map<String, Object> specs;
}
