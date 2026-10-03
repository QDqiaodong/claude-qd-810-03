package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.Borehole;

public interface BoreholeRepository extends JpaRepository<Borehole, Long> {

    List<Borehole> findByMineIdOrderByHoleNoAsc(Long mineId);

    List<Borehole> findByStatusOrderByHoleNoAsc(String status);

    boolean existsByHoleNo(String holeNo);
}
