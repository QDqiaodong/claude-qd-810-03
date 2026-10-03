package com.drill.crew.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.drill.crew.domain.ShiftReport;
import com.drill.crew.dto.RigEfficiencyDTO;

public interface ShiftReportRepository extends JpaRepository<ShiftReport, Long> {

    List<ShiftReport> findByRigIdOrderByReportDateDesc(Long rigId);

    List<ShiftReport> findByBoreholeIdOrderByReportDateDesc(Long boreholeId);

    List<ShiftReport> findAllByOrderByReportDateDesc();

    /**
     * 手写 JPQL ③：按钻机统计台月效率（台班数、累计进尺、纯钻/辅助工时）。
     */
    @Query("select new com.drill.crew.dto.RigEfficiencyDTO(r.id, r.code, r.model, r.status, count(s.id), "
            + "coalesce(sum(s.footage), cast(0 as BigDecimal)), "
            + "coalesce(sum(s.pureHours), cast(0 as BigDecimal)), "
            + "coalesce(sum(s.auxHours), cast(0 as BigDecimal))) "
            + "from ShiftReport s join s.rig r "
            + "group by r.id, r.code, r.model, r.status "
            + "order by coalesce(sum(s.footage), cast(0 as BigDecimal)) desc")
    List<RigEfficiencyDTO> statEfficiencyByRig();
}
