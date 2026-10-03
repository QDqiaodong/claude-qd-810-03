package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.DrillParamSeries;

public interface DrillParamSeriesRepository extends JpaRepository<DrillParamSeries, Long> {

    List<DrillParamSeries> findByBoreholeIdOrderByRecordDateDesc(Long boreholeId);

    List<DrillParamSeries> findAllByOrderByRecordDateDesc();
}
