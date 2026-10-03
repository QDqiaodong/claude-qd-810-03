package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.HandoverIssue;

public interface HandoverIssueRepository extends JpaRepository<HandoverIssue, Long> {

    List<HandoverIssue> findByHandoverIdOrderByIdAsc(Long handoverId);

    long countByHandoverIdAndStatus(Long handoverId, String status);
}
