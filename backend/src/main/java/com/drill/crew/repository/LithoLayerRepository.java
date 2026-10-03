package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.drill.crew.domain.LithoLayer;
import com.drill.crew.dto.LithoStatDTO;

public interface LithoLayerRepository extends JpaRepository<LithoLayer, Long> {

    List<LithoLayer> findByBoreholeIdOrderByFromDepthAsc(Long boreholeId);

    List<LithoLayer> findByRoundIdOrderByLayerSeqAsc(Long roundId);

    boolean existsByRoundIdAndLayerSeq(Long roundId, Integer layerSeq);

    /**
     * 手写 JPQL ②：按岩性统计层数与累计厚度（厚度占比在服务层回填）。
     */
    @Query("select new com.drill.crew.dto.LithoStatDTO(l.lithology, count(l.id), sum(l.toDepth - l.fromDepth)) "
            + "from LithoLayer l "
            + "group by l.lithology "
            + "order by sum(l.toDepth - l.fromDepth) desc")
    List<LithoStatDTO> statThicknessByLithology();
}
