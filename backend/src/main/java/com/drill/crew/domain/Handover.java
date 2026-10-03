package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.drill.crew.dto.HandoverSnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钻孔终孔交接版本。
 * <p>
 * 同一钻孔每发起一次、整改后每重新提交一次都新增一行；{@code activeFlag}=1 的行每孔至多一条，
 * 由 uk_handover_active(borehole_id, active_flag) 在数据库层兜底，防止两人同时推进产生重复生效版本。
 * <p>
 * 状态：PENDING_CHECK 待检查 / NEED_FIX 待整改 / ACCEPTED 已接收 / SUPERSEDED 已失效（返开）。
 * {@code lockVersion} 为手工乐观锁：退回、接收、返开都必须携带页面上看到的版本。
 */
@Entity
@Table(name = "handover")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 16)
    private String status;

    /** 1=当前生效版本；历史版本与返开失效版本为 NULL（参与唯一索引，NULL 不冲突） */
    @Column(name = "active_flag")
    private Integer activeFlag;

    @Column(name = "snapshot_depth", nullable = false, precision = 8, scale = 2)
    private BigDecimal snapshotDepth;

    /** 资料快照：发起/重新提交瞬间定稿，历史版本据此还原 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "json", nullable = false)
    private HandoverSnapshot snapshot;

    @Column(name = "issue_total", nullable = false)
    private Integer issueTotal;

    @Column(name = "issue_open", nullable = false)
    private Integer issueOpen;

    @Column(name = "submit_note", length = 255)
    private String submitNote;

    @Column(name = "initiated_by", length = 32)
    private String initiatedBy;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "checker_name", length = 32)
    private String checkerName;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    /** 本轮退回原因 */
    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    /** 上一轮退回原因（重新提交产生新版本时带出，保留检查痕迹） */
    @Column(name = "last_reject_reason", length = 255)
    private String lastRejectReason;

    /** 接收后返开原因（原接收版本标记失效时写入） */
    @Column(name = "reopen_reason", length = 255)
    private String reopenReason;

    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
