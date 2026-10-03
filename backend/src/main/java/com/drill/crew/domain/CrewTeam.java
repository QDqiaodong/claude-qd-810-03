package com.drill.crew.domain;

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
 * 班组。值班班别：早 / 中 / 夜；隶属于一台钻机。
 */
@Entity
@Table(name = "crew_team")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrewTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rig_id", nullable = false)
    private Long rigId;

    @Column(nullable = false, length = 32)
    private String leader;

    @Column(name = "worker_count", nullable = false)
    private Integer workerCount;

    @Column(name = "shift_type", nullable = false, length = 8)
    private String shiftType;

    @Column(length = 24)
    private String phone;

    @Column(length = 255)
    private String note;
}
