package com.drill.crew.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.drill.crew.domain.CoreSample;

import jakarta.persistence.LockModeType;

public interface CoreSampleRepository extends JpaRepository<CoreSample, Long> {

    /** 钻孔的全部样品，先正式后草稿、再按起深排序，取样登记页直接使用 */
    List<CoreSample> findByBoreholeIdOrderByStatusAscFromDepthAscIdAsc(Long boreholeId);

    /** 同孔非草稿样品（正式样品孔段重叠校验用），按起深排序 */
    List<CoreSample> findByBoreholeIdAndStatusNotOrderByFromDepthAscIdAsc(Long boreholeId, String status);

    /** 按状态查全部（待送检组批 / 台账状态筛选用） */
    List<CoreSample> findByStatusOrderByIdAsc(String status);

    List<CoreSample> findByBoreholeIdAndStatusOrderByIdAsc(Long boreholeId, String status);

    boolean existsBySampleNo(String sampleNo);

    boolean existsBySealNo(String sealNo);

    boolean existsBySampleNoAndIdNot(String sampleNo, Long id);

    boolean existsBySealNoAndIdNot(String sealNo, Long id);

    /**
     * 变更操作专用行锁：重复点击保存/交接时在此串行，配合 lockVersion 乐观锁返回 409。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CoreSample s where s.id = :id")
    Optional<CoreSample> findForUpdate(@Param("id") Long id);
}
