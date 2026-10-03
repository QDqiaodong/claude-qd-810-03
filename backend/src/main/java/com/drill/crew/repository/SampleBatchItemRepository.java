package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.SampleBatchItem;

public interface SampleBatchItemRepository extends JpaRepository<SampleBatchItem, Long> {

    List<SampleBatchItem> findByBatchIdOrderByIdAsc(Long batchId);

    List<SampleBatchItem> findBySampleIdOrderByIdAsc(Long sampleId);

    long countByBatchId(Long batchId);

    long countByBatchIdAndItemStatus(Long batchId, String itemStatus);
}
