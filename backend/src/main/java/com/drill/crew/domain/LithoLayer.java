package com.drill.crew.domain;

import java.math.BigDecimal;
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
 * 岩性分层 —— 本仓核心实体。
 * <p>
 * JSON 列用法 ①：attrs —— 「描述属性包」，承载含水性、硬度、裂隙发育、矿化、蚀变、
 * 取样编号等随岩性变化的字段，接口直接返回结构化内容。
 */
@Entity
@Table(name = "litho_layer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LithoLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "layer_seq", nullable = false)
    private Integer layerSeq;

    @Column(nullable = false, length = 32)
    private String lithology;

    @Column(name = "from_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal fromDepth;

    @Column(name = "to_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal toDepth;

    @Column(length = 24)
    private String color;

    @Column(length = 24)
    private String weathering;

    @Column(length = 32)
    private String structure;

    @Column(length = 255)
    private String description;

    /** JSON 列：岩性描述属性包（含水性 / 硬度 / 裂隙发育 / 矿化 / 蚀变 / 取样编号 …） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", columnDefinition = "json")
    private Map<String, Object> attrs;
}
