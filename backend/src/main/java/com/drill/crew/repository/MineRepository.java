package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.drill.crew.domain.Mine;
import com.drill.crew.dto.MineStatDTO;

public interface MineRepository extends JpaRepository<Mine, Long> {

    boolean existsByCode(String code);

    /**
     * 手写 JPQL ①：按矿区统计孔数与累计进尺。
     */
    @Query("select new com.drill.crew.dto.MineStatDTO(m.id, m.name, m.stage, count(b.id), "
            + "coalesce(sum(b.actualDepth), cast(0 as BigDecimal))) "
            + "from Mine m left join Borehole b on b.mine.id = m.id "
            + "group by m.id, m.name, m.stage "
            + "order by coalesce(sum(b.actualDepth), cast(0 as BigDecimal)) desc")
    List<MineStatDTO> statHoleCountAndFootage();
}
