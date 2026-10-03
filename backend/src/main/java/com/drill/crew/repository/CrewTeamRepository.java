package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.CrewTeam;

public interface CrewTeamRepository extends JpaRepository<CrewTeam, Long> {

    List<CrewTeam> findByRigIdOrderByIdAsc(Long rigId);
}
