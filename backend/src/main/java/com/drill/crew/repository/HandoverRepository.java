package com.drill.crew.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.drill.crew.domain.Handover;

import jakarta.persistence.LockModeType;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    /** 钻孔的当前生效版本（active_flag = 1，每孔至多一条） */
    Optional<Handover> findByBoreholeIdAndActiveFlag(Long boreholeId, Integer activeFlag);

    /** 钻孔的完整版本链，新提交在前 */
    List<Handover> findByBoreholeIdOrderByVersionNoDesc(Long boreholeId);

    /** 交接台列表：所有生效版本，可按状态过滤，按最近检查 / 发起时间倒序 */
    List<Handover> findByActiveFlagOrderByCheckedAtDescIdDesc(Integer activeFlag);

    List<Handover> findByActiveFlagAndStatusOrderByCheckedAtDescIdDesc(Integer activeFlag, String status);

    /** 下一个版本号：取当前最大版本号 + 1 */
    Optional<Handover> findFirstByBoreholeIdOrderByVersionNoDesc(Long boreholeId);

    long countByBoreholeId(Long boreholeId);

    /**
     * 变更操作专用：SELECT ... FOR UPDATE 行锁。
     * 两名负责人同时推进同一钻孔时在此串行 —— 后者等前者提交后读到新的 lockVersion，
     * 乐观锁校验失败返回 409，重新检查，而不会双双成功。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Handover h where h.id = :id")
    Optional<Handover> findForUpdate(@Param("id") Long id);
}
