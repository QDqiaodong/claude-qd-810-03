package com.drill.crew.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.common.ConflictException;
import com.drill.crew.common.HandoverDict;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CoreRound;
import com.drill.crew.domain.DrillParamSeries;
import com.drill.crew.domain.Handover;
import com.drill.crew.domain.HandoverIssue;
import com.drill.crew.domain.LithoLayer;
import com.drill.crew.domain.ShiftReport;
import com.drill.crew.dto.HandoverAction;
import com.drill.crew.dto.HandoverSnapshot;
import com.drill.crew.dto.IssueView;
import com.drill.crew.dto.SnapshotReport;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CoreRoundRepository;
import com.drill.crew.repository.DrillParamSeriesRepository;
import com.drill.crew.repository.HandoverIssueRepository;
import com.drill.crew.repository.HandoverRepository;
import com.drill.crew.repository.LithoLayerRepository;
import com.drill.crew.repository.ShiftReportRepository;

/**
 * 钻孔终孔交接台核心服务。
 * <p>
 * 生命周期（生效版本每孔至多一条，由 uk_handover_active 兜底）：
 * <pre>
 *   班组发起 ──► PENDING_CHECK（以当时实际孔深形成资料快照 + 问题项）
 *   负责人退回 ──► NEED_FIX（保留退回原因与上一轮检查结果；未清零问题只能保存整改进度）
 *   班组重新提交 ──► 新版本 PENDING_CHECK（旧版本转为历史，问题项按稳定 key 带到新版本）
 *   负责人接收 ──► ACCEPTED（必须实时复查无待处理问题；接收后资料冻结）
 *   负责人返开 ──► 新版本 NEED_FIX（原接收版本保留并标记 SUPERSEDED 已失效）
 * </pre>
 * lockVersion 乐观锁保证两名负责人同时处理时，后提交者收到 409、重新检查，不产生重复版本。
 */
@Service
public class HandoverService {

    private final HandoverRepository handoverRepository;
    private final HandoverIssueRepository issueRepository;
    private final BoreholeRepository boreholeRepository;
    private final CoreRoundRepository roundRepository;
    private final LithoLayerRepository layerRepository;
    private final DrillParamSeriesRepository seriesRepository;
    private final ShiftReportRepository reportRepository;
    private final HandoverInspector inspector;

    public HandoverService(HandoverRepository handoverRepository, HandoverIssueRepository issueRepository,
                           BoreholeRepository boreholeRepository, CoreRoundRepository roundRepository,
                           LithoLayerRepository layerRepository, DrillParamSeriesRepository seriesRepository,
                           ShiftReportRepository reportRepository, HandoverInspector inspector) {
        this.handoverRepository = handoverRepository;
        this.issueRepository = issueRepository;
        this.boreholeRepository = boreholeRepository;
        this.roundRepository = roundRepository;
        this.layerRepository = layerRepository;
        this.seriesRepository = seriesRepository;
        this.reportRepository = reportRepository;
        this.inspector = inspector;
    }

    // ---------------- 查询 ----------------

    /** 交接台列表：各钻孔当前生效版本，可按状态过滤 */
    @Transactional(readOnly = true)
    public List<Handover> listActive(String status) {
        List<Handover> list = (status == null || status.isBlank())
                ? handoverRepository.findByActiveFlagOrderByCheckedAtDescIdDesc(1)
                : handoverRepository.findByActiveFlagAndStatusOrderByCheckedAtDescIdDesc(1, status);
        list.forEach(h -> h.setSnapshot(null)); // 列表不回传大快照
        return list;
    }

    /** 钻孔的版本链（不含快照大字段） */
    @Transactional(readOnly = true)
    public List<Handover> listVersions(Long boreholeId) {
        requireHole(boreholeId);
        List<Handover> list = handoverRepository.findByBoreholeIdOrderByVersionNoDesc(boreholeId);
        list.forEach(h -> h.setSnapshot(null));
        return list;
    }

    /** 版本详情：交接元数据 + 当时资料快照 + 该版本问题项（历史版本据此还原） */
    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(Long id) {
        Handover handover = getHandover(id);
        List<IssueView> issues = issueRepository.findByHandoverIdOrderByIdAsc(id).stream()
                .map(IssueView::of).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handover", handover);
        result.put("issues", issues);
        return result;
    }

    /**
     * 按【实时资料】检查钻孔，返回当前仍存在的问题项（不写库）。
     * 负责人检查页面与「保存整改进度」都以此反映整改后的最新状态。
     */
    @Transactional(readOnly = true)
    public List<IssueView> liveIssues(Long boreholeId) {
        Borehole hole = requireHole(boreholeId);
        return inspector.inspect(boreholeId, hole.getActualDepth()).stream()
                .map(IssueView::of).collect(Collectors.toList());
    }

    // ---------------- 班组：发起交接 ----------------

    @Transactional
    public Handover initiate(Long boreholeId, HandoverAction action) {
        Borehole hole = requireHole(boreholeId);
        if (!"终孔".equals(hole.getStatus())) {
            throw new BizException("钻孔【" + hole.getHoleNo() + "】当前为「" + hole.getStatus()
                    + "」状态，只有终孔钻孔才能发起终孔交接");
        }
        if (handoverRepository.findByBoreholeIdAndActiveFlag(boreholeId, 1).isPresent()) {
            throw new ConflictException("钻孔【" + hole.getHoleNo() + "】已存在交接流程，可能由他人同时发起，请刷新后重新检查");
        }

        int versionNo = handoverRepository.findFirstByBoreholeIdOrderByVersionNoDesc(boreholeId)
                .map(h -> h.getVersionNo() + 1).orElse(1);
        Handover handover = Handover.builder()
                .boreholeId(boreholeId)
                .versionNo(versionNo)
                .status(HandoverDict.PENDING_CHECK)
                .activeFlag(1)
                .submitNote(trim(action.getNote()))
                .initiatedBy(trim(action.getOperator()))
                .initiatedAt(LocalDateTime.now())
                .issueTotal(0)
                .issueOpen(0)
                .lockVersion(0)
                .createdAt(LocalDateTime.now())
                .build();
        // 发起时以当时的实际孔深形成资料快照
        handover.setSnapshotDepth(hole.getActualDepth());
        handover.setSnapshot(buildSnapshot(hole));
        handover = saveActive(handover);
        refreshIssues(handover, null);
        return getHandover(handover.getId());
    }

    // ---------------- 班组：保存整改进度（不推进状态） ----------------

    @Transactional
    public Map<String, Object> saveProgress(Long id, HandoverAction action) {
        Handover handover = lockHandover(id, action.getExpectedVersion());
        requireActive(handover);
        if (!HandoverDict.NEED_FIX.equals(handover.getStatus())) {
            throw new BizException("当前状态为「" + HandoverDict.statusName(handover.getStatus())
                    + "」，只有待整改版本需要保存整改进度");
        }
        // 按实时资料重算：已消除的问题标记为已处理，新冒出的问题补进来；问题未清零也允许保存
        int changed = refreshIssues(handover, null);
        handover.setLockVersion(handover.getLockVersion() + 1);
        handover = handoverRepository.save(handover);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handover", getHandover(handover.getId()));
        result.put("issues", listIssueViews(handover.getId()));
        result.put("changed", changed);
        return result;
    }

    // ---------------- 班组：整改后重新提交（产生新版本） ----------------

    @Transactional
    public Handover resubmit(Long id, HandoverAction action) {
        Handover prev = lockHandover(id, action.getExpectedVersion());
        requireActive(prev);
        if (!HandoverDict.NEED_FIX.equals(prev.getStatus())) {
            throw new BizException("当前状态为「" + HandoverDict.statusName(prev.getStatus())
                    + "」，只有被退回待整改的版本才能整改后重新提交");
        }

        Borehole hole = requireHole(prev.getBoreholeId());
        List<HandoverIssue> prevIssues = issueRepository.findByHandoverIdOrderByIdAsc(prev.getId());

        // 新版本重新以当前实际孔深快照，旧版本降为历史（active_flag = NULL，保留状态与检查结果）。
        // 先 flush 去活，再插入新的 active=1，避免同一事务内 uk_handover_active 瞬时竞争。
        prev.setActiveFlag(null);
        handoverRepository.saveAndFlush(prev);

        Handover next = Handover.builder()
                .boreholeId(hole.getId())
                .versionNo(prev.getVersionNo() + 1)
                .status(HandoverDict.PENDING_CHECK)
                .activeFlag(1)
                .snapshotDepth(hole.getActualDepth())
                .snapshot(buildSnapshot(hole))
                .submitNote(trim(action.getNote()))
                .initiatedBy(trim(action.getOperator()))
                .initiatedAt(LocalDateTime.now())
                // 保留上一轮检查结果，可追溯
                .lastRejectReason(prev.getRejectReason())
                .issueTotal(0)
                .issueOpen(0)
                .lockVersion(0)
                .createdAt(LocalDateTime.now())
                .build();
        next = saveActive(next);
        refreshIssues(next, prevIssues);
        return getHandover(next.getId());
    }

    // ---------------- 负责人：退回整改 ----------------

    @Transactional
    public Handover reject(Long id, HandoverAction action) {
        String reason = trim(action.getReason());
        if (reason == null || reason.isEmpty()) {
            throw new BizException("退回整改必须填写退回原因，便于班组对照整改");
        }
        Handover handover = lockHandover(id, action.getExpectedVersion());
        requireActive(handover);
        if (!HandoverDict.PENDING_CHECK.equals(handover.getStatus())) {
            throw new BizException("只有待检查的版本才能退回，当前为「" + HandoverDict.statusName(handover.getStatus()) + "」");
        }
        // 退回前按实时资料刷新一遍问题项，作为本轮检查结果留给班组
        refreshIssues(handover, null);

        handover.setStatus(HandoverDict.NEED_FIX);
        handover.setRejectReason(reason);
        handover.setCheckerName(trim(action.getOperator()));
        handover.setCheckedAt(LocalDateTime.now());
        handover.setLockVersion(handover.getLockVersion() + 1);
        return handoverRepository.save(handover);
    }

    // ---------------- 负责人：确认接收 ----------------

    @Transactional
    public Handover accept(Long id, HandoverAction action) {
        Handover handover = lockHandover(id, action.getExpectedVersion());
        requireActive(handover);
        if (!HandoverDict.PENDING_CHECK.equals(handover.getStatus())) {
            throw new BizException("只有待检查的版本才能确认接收，当前为「" + HandoverDict.statusName(handover.getStatus()) + "」");
        }

        // 接收前必须按实时资料复查：问题未清零只能退回整改，不能接收
        Borehole hole = requireHole(handover.getBoreholeId());
        List<HandoverInspector.IssueSpec> live = inspector.inspect(hole.getId(), hole.getActualDepth());
        if (!live.isEmpty()) {
            String first = live.get(0).message;
            throw new BizException("仍有 " + live.size() + " 个待处理问题未清零（首个：" + first
                    + "），不能确认接收，请退回整改后重新提交");
        }

        // 实时资料已全部合格：历史问题项（本版本快照曾检出的）一并标记已处理并随版本留痕
        for (HandoverIssue issue : issueRepository.findByHandoverIdOrderByIdAsc(id)) {
            issue.setStatus(HandoverDict.ISSUE_RESOLVED);
            issueRepository.save(issue);
        }
        handover.setIssueTotal(handover.getIssueTotal() == null ? 0 : handover.getIssueTotal());
        handover.setIssueOpen(0);
        handover.setStatus(HandoverDict.ACCEPTED);
        handover.setCheckerName(trim(action.getOperator()));
        handover.setCheckedAt(LocalDateTime.now());
        handover.setLockVersion(handover.getLockVersion() + 1);
        return handoverRepository.save(handover);
    }

    // ---------------- 负责人：接收后返开 ----------------

    @Transactional
    public Handover reopen(Long id, HandoverAction action) {
        String reason = trim(action.getReason());
        if (reason == null || reason.isEmpty()) {
            throw new BizException("返开已接收钻孔必须填写返开原因");
        }
        Handover accepted = lockHandover(id, action.getExpectedVersion());
        requireActive(accepted);
        if (!HandoverDict.ACCEPTED.equals(accepted.getStatus())) {
            throw new BizException("只有已接收的版本才能返开，当前为「" + HandoverDict.statusName(accepted.getStatus()) + "」");
        }

        Borehole hole = requireHole(accepted.getBoreholeId());

        // 原接收版本继续保留，但清楚标记为【已失效】，旧结论不会被新数据悄悄覆盖。
        // 先 flush 去活再插入新版本，避免同一事务内 uk_handover_active 瞬时竞争。
        accepted.setActiveFlag(null);
        accepted.setStatus(HandoverDict.SUPERSEDED);
        accepted.setReopenReason(reason);
        accepted.setLockVersion(accepted.getLockVersion() + 1);
        handoverRepository.saveAndFlush(accepted);

        Handover next = Handover.builder()
                .boreholeId(hole.getId())
                .versionNo(accepted.getVersionNo() + 1)
                .status(HandoverDict.NEED_FIX)
                .activeFlag(1)
                .snapshotDepth(hole.getActualDepth())
                .snapshot(buildSnapshot(hole))
                .initiatedBy(accepted.getInitiatedBy())
                .initiatedAt(LocalDateTime.now())
                .rejectReason("返开整改：" + reason)
                .reopenReason(reason)
                .checkerName(trim(action.getOperator()))
                .checkedAt(LocalDateTime.now())
                .issueTotal(0)
                .issueOpen(0)
                .lockVersion(0)
                .createdAt(LocalDateTime.now())
                .build();
        next = saveActive(next);
        refreshIssues(next, null);
        return getHandover(next.getId());
    }

    // ---------------- 内部规则 ----------------

    private Handover getHandover(Long id) {
        return handoverRepository.findById(id)
                .orElseThrow(() -> new BizException("交接版本不存在：id=" + id));
    }

    /** 变更操作取版本：行锁 + 乐观锁一并在此完成，保证两名负责人并发推进时后提交者收到 409 */
    private Handover lockHandover(Long id, Integer expectedVersion) {
        Handover handover = handoverRepository.findForUpdate(id)
                .orElseThrow(() -> new BizException("交接版本不存在：id=" + id));
        checkLock(handover, expectedVersion);
        return handover;
    }

    private Borehole requireHole(Long boreholeId) {
        if (boreholeId == null) {
            throw new BizException("必须指定钻孔");
        }
        return boreholeRepository.findById(boreholeId)
                .orElseThrow(() -> new BizException("钻孔不存在：id=" + boreholeId));
    }

    private void requireActive(Handover handover) {
        if (handover.getActiveFlag() == null || handover.getActiveFlag() != 1) {
            throw new ConflictException("该版本已不是当前生效版本（另一位负责人可能已推进：被整改新版本取代或已返开失效），"
                    + "请刷新后在最新版本上重新检查");
        }
    }

    /** 乐观锁校验：两人同时处理时，后提交者持有的版本已过期 */
    private void checkLock(Handover handover, Integer expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(handover.getLockVersion())) {
            throw new ConflictException("交接数据已被另一位负责人更新（当前版本号 " + handover.getLockVersion()
                    + "，你看到的是 " + expectedVersion + "），请重新检查后再操作");
        }
    }

    /**
     * 保存「每孔至多一个生效版本」，并发下唯一约束兜底，转为 409 而不是产生重复版本。
     */
    private Handover saveActive(Handover handover) {
        try {
            return handoverRepository.saveAndFlush(handover);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("该钻孔同时存在另一个正在推进的交接版本，本次操作未生效，请刷新后重新检查");
        }
    }

    /**
     * 按实时资料刷新版本问题项。
     * <p>
     * 以 issue_key 为稳定标识：原问题在实时资料中仍存在 → 保持 OPEN；已消失 → RESOLVED；
     * 实时资料中新冒出的问题 → 新增 OPEN。问题计数回写到版本头。
     *
     * @param previous 上一版本（重新提交时）的问题项，用于把「已处理」结论带进新版本
     * @return 状态发生变化的问题条数（供前端提示）
     */
    private int refreshIssues(Handover handover, List<HandoverIssue> previous) {
        Borehole hole = requireHole(handover.getBoreholeId());
        List<HandoverInspector.IssueSpec> liveSpecs = inspector.inspect(hole.getId(), hole.getActualDepth());
        Map<String, HandoverInspector.IssueSpec> liveByKey = new LinkedHashMap<>();
        for (HandoverInspector.IssueSpec spec : liveSpecs) {
            liveByKey.putIfAbsent(spec.issueKey, spec);
        }

        List<HandoverIssue> existing = issueRepository.findByHandoverIdOrderByIdAsc(handover.getId());
        Map<String, HandoverIssue> existByKey = new LinkedHashMap<>();
        for (HandoverIssue e : existing) {
            existByKey.putIfAbsent(e.getIssueKey(), e);
        }
        // 上一版本（重新提交 / 返开）已处理过的问题，在第 3 步复制为 RESOLVED 留痕
        int changed = 0;

        // 1) 实时资料中仍存在的问题：落为 OPEN；原记录若已是 RESOLVED 则重新打开
        for (HandoverInspector.IssueSpec spec : liveSpecs) {
            HandoverIssue row = existByKey.get(spec.issueKey);
            if (row == null) {
                row = HandoverIssue.builder()
                        .handoverId(handover.getId())
                        .issueType(spec.issueType)
                        .targetType(spec.targetType)
                        .targetId(spec.targetId)
                        .targetLabel(spec.targetLabel)
                        .issueKey(spec.issueKey)
                        .message(spec.message)
                        .status(HandoverDict.ISSUE_OPEN)
                        .build();
                issueRepository.save(row);
                changed++;
            } else if (!HandoverDict.ISSUE_OPEN.equals(row.getStatus())) {
                row.setStatus(HandoverDict.ISSUE_OPEN);
                issueRepository.save(row);
                changed++;
            }
        }

        // 2) 版本里有、但实时资料已消失的问题：标记 RESOLVED
        for (HandoverIssue row : existing) {
            if (!liveByKey.containsKey(row.getIssueKey())
                    && !HandoverDict.ISSUE_RESOLVED.equals(row.getStatus())) {
                row.setStatus(HandoverDict.ISSUE_RESOLVED);
                issueRepository.save(row);
                changed++;
            }
        }

        // 3) 重新提交：上一版本已处理、本版本首次同步且实时未再出现的问题，复制为 RESOLVED 留痕
        if (previous != null) {
            for (HandoverIssue old : previous) {
                if (HandoverDict.ISSUE_RESOLVED.equals(old.getStatus())
                        && !liveByKey.containsKey(old.getIssueKey())
                        && !existByKey.containsKey(old.getIssueKey())) {
                    issueRepository.save(HandoverIssue.builder()
                            .handoverId(handover.getId())
                            .issueType(old.getIssueType())
                            .targetType(old.getTargetType())
                            .targetId(old.getTargetId())
                            .targetLabel(old.getTargetLabel())
                            .issueKey(old.getIssueKey())
                            .message(old.getMessage())
                            .status(HandoverDict.ISSUE_RESOLVED)
                            .build());
                }
            }
        }

        List<HandoverIssue> all = issueRepository.findByHandoverIdOrderByIdAsc(handover.getId());
        long open = all.stream().filter(i -> HandoverDict.ISSUE_OPEN.equals(i.getStatus())).count();
        handover.setIssueTotal(all.size());
        handover.setIssueOpen((int) open);
        handoverRepository.save(handover);
        return changed;
    }

    private List<IssueView> listIssueViews(Long handoverId) {
        return issueRepository.findByHandoverIdOrderByIdAsc(handoverId).stream()
                .map(IssueView::of).collect(Collectors.toList());
    }

    /** 发起 / 重新提交 / 返开瞬间，以当时实际孔深形成资料快照 */
    private HandoverSnapshot buildSnapshot(Borehole hole) {
        List<CoreRound> rounds = roundRepository.findByBoreholeIdOrderByRoundNoAsc(hole.getId());
        List<LithoLayer> layers = layerRepository.findByBoreholeIdOrderByFromDepthAsc(hole.getId());
        List<DrillParamSeries> series = seriesRepository.findByBoreholeIdOrderByRecordDateDesc(hole.getId());
        List<SnapshotReport> reports = reportRepository.findByBoreholeIdOrderByReportDateDesc(hole.getId())
                .stream().map(this::toSnapshotReport).collect(Collectors.toList());

        // 岩性层按回次+层序排，保证快照柱状剖面顺序稳定
        layers.sort(Comparator.comparing(LithoLayer::getRoundId).thenComparing(LithoLayer::getLayerSeq));

        return HandoverSnapshot.builder()
                .hole(HandoverSnapshot.HoleSnapshot.builder()
                        .id(hole.getId())
                        .holeNo(hole.getHoleNo())
                        .mineId(hole.getMineId())
                        .rigId(hole.getRigId())
                        .designDepth(hole.getDesignDepth())
                        .actualDepth(hole.getActualDepth())
                        .spudDate(hole.getSpudDate())
                        .finalDate(hole.getFinalDate())
                        .dipAngle(hole.getDipAngle())
                        .status(hole.getStatus())
                        .build())
                .actualDepth(hole.getActualDepth())
                .rounds(rounds)
                .layers(layers)
                .paramSeries(series)
                .reports(reports)
                .roundCount(rounds.size())
                .layerCount(layers.size())
                .seriesCount(series.size())
                .reportCount(reports.size())
                .build();
    }

    private SnapshotReport toSnapshotReport(ShiftReport r) {
        return SnapshotReport.builder()
                .id(r.getId())
                .boreholeId(r.getBoreholeId())
                .rigId(r.getRigId())
                .crewId(r.getCrewId())
                .reportDate(r.getReportDate())
                .shiftType(r.getShiftType())
                .footage(r.getFootage())
                .pureHours(r.getPureHours())
                .auxHours(r.getAuxHours())
                .accidentNote(r.getAccidentNote())
                .materials(r.getMaterials() == null ? new ArrayList<>() : r.getMaterials())
                .handoverNote(r.getHandoverNote())
                .leaderSign(r.getLeaderSign())
                .build();
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
