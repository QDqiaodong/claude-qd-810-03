package com.drill.crew.dto;

import com.drill.crew.service.HandoverInspector;
import com.drill.crew.service.HandoverInspector.IssueSpec;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 问题项视图：在检查结果上附加中文类型名、跳转页面与处理状态，
 * 交接台据此把问题项分组展示并提供「去整改」直跳入口。
 */
@Getter
@Setter
@NoArgsConstructor
public class IssueView {

    private Long id;
    private Long handoverId;
    private String issueType;
    private String typeName;
    private String targetType;
    private Long targetId;
    private String targetLabel;
    private String targetPage;
    private String issueKey;
    private String message;
    private String status;

    /** 实时检查结果（尚未入库）转视图 */
    public static IssueView of(IssueSpec spec) {
        IssueView v = new IssueView();
        v.issueType = spec.issueType;
        v.typeName = HandoverInspector.typeName(spec.issueType);
        v.targetType = spec.targetType;
        v.targetId = spec.targetId;
        v.targetLabel = spec.targetLabel;
        v.targetPage = HandoverInspector.targetPage(spec.targetType);
        v.issueKey = spec.issueKey;
        v.message = spec.message;
        v.status = "OPEN";
        return v;
    }

    /** 已持久化的问题项转视图 */
    public static IssueView of(com.drill.crew.domain.HandoverIssue e) {
        IssueView v = new IssueView();
        v.id = e.getId();
        v.handoverId = e.getHandoverId();
        v.issueType = e.getIssueType();
        v.typeName = HandoverInspector.typeName(e.getIssueType());
        v.targetType = e.getTargetType();
        v.targetId = e.getTargetId();
        v.targetLabel = e.getTargetLabel();
        v.targetPage = HandoverInspector.targetPage(e.getTargetType());
        v.issueKey = e.getIssueKey();
        v.message = e.getMessage();
        v.status = e.getStatus();
        return v;
    }
}
