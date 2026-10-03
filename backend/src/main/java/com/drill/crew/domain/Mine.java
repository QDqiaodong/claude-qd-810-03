package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * 矿区。勘查阶段：预查 / 普查 / 详查 / 勘探。
 */
@Entity
@Table(name = "mine_area")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 16)
    private String stage;

    @Column(length = 64)
    private String location;

    @Column(name = "area_km2", precision = 10, scale = 2)
    private BigDecimal areaKm2;

    @Column(name = "chief_geologist", length = 32)
    private String chiefGeologist;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(length = 255)
    private String remark;
}
