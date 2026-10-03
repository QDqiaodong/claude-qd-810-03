package com.drill.crew.domain;

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
 * 交接问题项：检查时按当前资料生成，随版本一起保存。
 * <p>
 * issue_key 是问题的稳定标识（如 ROUND_LOW_RECOVERY:3、REPORT_UNSIGNED:8），
 * 保存整改进度 / 重新提交时按当前资料重算：key 仍在 = OPEN，已消失 = RESOLVED。
 * 问题项通过 target_type + target_id 直接跳回对应资料继续整改。
 */
@Entity
@Table(name = "handover_issue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoverIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    /** ROUND_GAP / ROUND_LOW_RECOVERY / LAYER_GAP / PARAM_ABNORMAL / REPORT_UNSIGNED */
    @Column(name = "issue_type", nullable = false, length = 22)
    private String issueType;

    /** round / layer / series / report */
    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_label", length = 128)
    private String targetLabel;

    @Column(name = "issue_key", nullable = false, length = 120)
    private String issueKey;

    @Column(nullable = false, length = 255)
    private String message;

    /** OPEN / RESOLVED */
    @Column(nullable = false, length = 10)
    private String status;
}
