package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * 钻孔。状态：施工 / 终孔 / 封孔。
 */
@Entity
@Table(name = "borehole")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Borehole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mine_id", nullable = false)
    private Long mineId;

    @Column(name = "rig_id")
    private Long rigId;

    @Column(name = "hole_no", nullable = false, unique = true, length = 32)
    private String holeNo;

    @Column(name = "coord_x", nullable = false, precision = 12, scale = 2)
    private BigDecimal coordX;

    @Column(name = "coord_y", nullable = false, precision = 14, scale = 2)
    private BigDecimal coordY;

    @Column(name = "design_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal designDepth;

    @Column(name = "actual_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal actualDepth;

    @Column(name = "spud_date")
    private LocalDate spudDate;

    @Column(name = "final_date")
    private LocalDate finalDate;

    @Column(name = "collar_elev", precision = 8, scale = 2)
    private BigDecimal collarElev;

    @Column(name = "dip_angle", precision = 5, scale = 2)
    private BigDecimal dipAngle;

    @Column(nullable = false, length = 16)
    private String status;

    /** 只读导航属性，供 JPQL 按矿区分组统计使用 */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mine_id", insertable = false, updatable = false)
    private Mine mine;
}
