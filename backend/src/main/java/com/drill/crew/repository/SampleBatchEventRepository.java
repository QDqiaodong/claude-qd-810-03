package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.SampleBatchEvent;

public interface SampleBatchEventRepository extends JpaRepository<SampleBatchEvent, Long> {

    List<SampleBatchEvent> findByBatchIdOrderByEventAtAscIdAsc(Long batchId);
}
