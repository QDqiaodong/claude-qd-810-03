package com.drill.crew.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.drill.crew.domain.SampleBatch;

import jakarta.persistence.LockModeType;

public interface SampleBatchRepository extends JpaRepository<SampleBatch, Long> {

    List<SampleBatch> findAllByOrderByIdDesc();

    List<SampleBatch> findByStatusOrderByIdDesc(String status);

    boolean existsByBatchNo(String batchNo);

    /** 批次编号：取当天最大序号（SJ-yyyyMMdd-xx） */
    Optional<SampleBatch> findFirstByBatchNoStartingWithOrderByBatchNoDesc(String prefix);

    /**
     * 交接 / 接收 / 退回专用行锁：重复点击在数据库层串行，
     * 配合 lockVersion 乐观锁，后提交者收到 409，不会产生重复交接记录。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SampleBatch b where b.id = :id")
    Optional<SampleBatch> findForUpdate(@Param("id") Long id);
}
