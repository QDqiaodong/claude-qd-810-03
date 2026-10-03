package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drill.crew.domain.CoreRound;

public interface CoreRoundRepository extends JpaRepository<CoreRound, Long> {

    List<CoreRound> findByBoreholeIdOrderByRoundNoAsc(Long boreholeId);

    boolean existsByBoreholeIdAndRoundNo(Long boreholeId, Integer roundNo);

    /** 低采取率（&lt; 75%）回次，用于编录质量核查 */
    List<CoreRound> findByRecoveryRateLessThanOrderByRecoveryRateAsc(java.math.BigDecimal threshold);
}
