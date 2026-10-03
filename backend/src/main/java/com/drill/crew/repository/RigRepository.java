package com.drill.crew.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.Rig;

public interface RigRepository extends JpaRepository<Rig, Long> {

    boolean existsByCode(String code);
}
