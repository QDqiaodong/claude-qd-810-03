package com.drill.crew.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.common.ConflictException;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.domain.SampleBatch;
import com.drill.crew.domain.SampleBatchEvent;
import com.drill.crew.domain.SampleBatchItem;
import com.drill.crew.dto.BatchPackRequest;
import com.drill.crew.dto.BatchReceiveRequest;
import com.drill.crew.dto.BatchReturnRequest;
import com.drill.crew.repository.CoreSampleRepository;
import com.drill.crew.repository.SampleBatchEventRepository;
import com.drill.crew.repository.SampleBatchItemRepository;
import com.drill.crew.repository.SampleBatchRepository;

/**
 * 送检批次：封样运输 → 实验室接收 / 部分接收 / 整批退回。
 * <p>
 * 状态机（{@link SampleDict}）：
 * <pre>
 *   封样发运      IN_TRANSIT（样品 REGISTERED → IN_TRANSIT，明细快照定稿，追加 PACK 事件）
 *   整批退回      RETURNED  （样品回到 REGISTERED 可重新组批，明细标 RETURNED，追加 RETURN 事件）
 *   全部一致接收   RECEIVED （样品全部 RECEIVED，追加 RECEIVE 事件）
 *   部分异常接收   PARTIAL_RECEIVED（一致样品 RECEIVED 保留，异常样品 ABNORMAL 标待补，追加 PARTIAL_RECEIVE 事件）
 * </pre>
 * 接收三核对：状态正确（运输中、未被退回/接收过）、数量一致（逐行覆盖封样数量）、封签一致（逐行核对）。
 * 行锁 + lockVersion 乐观锁保证重复点击交接或接收只产生一次结果；事件表只增不改，历史交接结果可追溯。
 */
@Service
public class SampleBatchService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SampleBatchRepository batchRepository;
    private final SampleBatchItemRepository itemRepository;
    private final SampleBatchEventRepository eventRepository;
    private final CoreSampleRepository sampleRepository;

    public SampleBatchService(SampleBatchRepository batchRepository, SampleBatchItemRepository itemRepository,
                              SampleBatchEventRepository eventRepository, CoreSampleRepository sampleRepository) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.eventRepository = eventRepository;
        this.sampleRepository = sampleRepository;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<SampleBatch> list(String status) {
        List<SampleBatch> list = (status == null || status.isBlank())
                ? batchRepository.findAllByOrderByIdDesc()
                : batchRepository.findByStatusOrderByIdDesc(status);
        return list;
    }

    /** 批次详情：批次头 + 样品明细（含行级接收结果）+ 交接事件链 */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        SampleBatch batch = getBatch(id);
        List<SampleBatchItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
        List<SampleBatchEvent> events = eventRepository.findByBatchIdOrderByEventAtAscIdAsc(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("items", items);
        result.put("events", events);
        return result;
    }

    // ---------------- 现场：封样发运 ----------------

    @Transactional
    public Map<String, Object> pack(BatchPackRequest request) {
        List<Long> ids = request.getSampleIds();
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请至少选择一个样品再封样发运");
        }
        String packer = trim(request.getPacker());
        if (packer == null) {
            throw new BizException("封样人必须填写，交接时要能追溯到封样责任人");
        }

        // 逐样加行锁：与「重复点击组批 / 样品被其它批次同时封走」串行
        List<CoreSample> samples = loadSamplesForPack(ids);

        LocalDateTime now = LocalDateTime.now();
        SampleBatch batch = SampleBatch.builder()
                .batchNo(generateBatchNo(now.toLocalDate()))
                .status(SampleDict.B_IN_TRANSIT)
                .expectedCount(samples.size())
                .receivedCount(0)
                .abnormalCount(0)
                .packer(packer)
                .carrier(trim(request.getCarrier()))
                .packNote(trim(request.getNote()))
                .packedAt(now)
                .lockVersion(0)
                .createdAt(now)
                .build();
        try {
            batch = batchRepository.saveAndFlush(batch);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("批次编号生成冲突，可能有人同时封样，请重试");
        }

        // 明细按样品快照定稿；样品置运输中并挂到批次
        List<SampleBatchItem> items = new ArrayList<>();
        for (CoreSample s : samples) {
            s.setStatus(SampleDict.S_IN_TRANSIT);
            s.setBatchId(batch.getId());
            s.setUpdatedAt(now);
            sampleRepository.save(s);
            items.add(SampleBatchItem.builder()
                    .batchId(batch.getId())
                    .sampleId(s.getId())
                    .sampleNo(s.getSampleNo())
                    .boreholeId(s.getBoreholeId())
                    .fromDepth(s.getFromDepth())
                    .toDepth(s.getToDepth())
                    .sealNo(s.getSealNo())
                    .itemStatus(SampleDict.ITEM_PENDING)
                    .build());
        }
        itemRepository.saveAll(items);

        addEvent(batch.getId(), SampleDict.EV_PACK, SampleDict.B_IN_TRANSIT, samples.size(), 0, 0,
                packer, trim(request.getNote()), now);
        return detail(batch.getId());
    }

    /** 加载并校验待封样样品：去重、存在、必须为【已登记待送检】且未挂其它批次 */
    private List<CoreSample> loadSamplesForPack(List<Long> ids) {
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new BizException("样品明细存在重复勾选，请去重后再封样");
        }
        List<CoreSample> samples = new ArrayList<>();
        for (Long id : uniqueIds) {
            CoreSample s = sampleRepository.findForUpdate(id)
                    .orElseThrow(() -> new BizException("样品不存在：id=" + id));
            if (!SampleDict.S_REGISTERED.equals(s.getStatus())) {
                throw new BizException("样品【" + (s.getSampleNo() == null ? s.getId() : s.getSampleNo())
                        + "】当前状态为「" + SampleDict.sampleStatusName(s.getStatus())
                        + "」，只有已登记待送检的样品能封样发运");
            }
            if (s.getBatchId() != null) {
                throw new BizException("样品【" + s.getSampleNo() + "】已在其它送检批次中，不能重复封样");
            }
            samples.add(s);
        }
        return samples;
    }

    // ---------------- 实验室：接收（全部 / 部分） ----------------

    @Transactional
    public Map<String, Object> receive(Long id, BatchReceiveRequest request) {
        SampleBatch batch = lockBatch(id, request.getExpectedVersion());
        requireInTransit(batch, "接收");
        List<SampleBatchItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
        String receiver = trim(request.getReceiver());
        if (receiver == null) {
            throw new BizException("实验室接收人必须填写");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BizException("请逐样核对并回写接收结果（数量、封签逐项确认）");
        }

        // 以样品 id 建立明细索引，逐行核对：必须完整覆盖封样明细，不能多也不能少（数量一致）
        Map<Long, SampleBatchItem> itemBySample = new LinkedHashMap<>();
        for (SampleBatchItem item : items) {
            itemBySample.put(item.getSampleId(), item);
        }
        Set<Long> lineSampleIds = new HashSet<>();
        for (BatchReceiveRequest.Line line : request.getItems()) {
            if (line.getSampleId() == null) {
                throw new BizException("接收结果缺少样品标识，请逐行核对后再提交");
            }
            if (!lineSampleIds.add(line.getSampleId())) {
                throw new BizException("样品【" + line.getSampleId() + "】出现多条接收结果，不能重复核对");
            }
            if (!itemBySample.containsKey(line.getSampleId())) {
                throw new BizException("收到的样品【" + line.getSampleId()
                        + "】不在本批次封样明细中（数量多于封样数量），请整批退回或核对送样单");
            }
        }
        if (lineSampleIds.size() != items.size()) {
            throw new BizException("实到样品 " + lineSampleIds.size() + " 件与封样数量 " + items.size()
                    + " 件不一致，请逐行核对；若无法确认来源应整批退回");
        }

        // 逐行回写：abnormal=false 正常接收（数量、封签一致）；abnormal=true 必须写异常说明
        LocalDateTime now = LocalDateTime.now();
        int received = 0;
        int abnormal = 0;
        for (BatchReceiveRequest.Line line : request.getItems()) {
            SampleBatchItem item = itemBySample.get(line.getSampleId());
            CoreSample sample = sampleRepository.findForUpdate(item.getSampleId())
                    .orElseThrow(() -> new BizException("样品不存在：id=" + item.getSampleId()));
            if (line.isAbnormal()) {
                String note = trim(line.getAbnormalNote());
                if (note == null) {
                    throw new BizException("样品【" + item.getSampleNo() + "】标记为异常时必须填写异常说明（待补项）");
                }
                item.setItemStatus(SampleDict.ITEM_ABNORMAL);
                item.setAbnormalNote(note);
                item.setReceivedAt(null);
                item.setReceiver(null);
                sample.setStatus(SampleDict.S_ABNORMAL);
                abnormal++;
            } else {
                item.setItemStatus(SampleDict.ITEM_RECEIVED);
                item.setAbnormalNote(null);
                item.setReceivedAt(now);
                item.setReceiver(receiver);
                sample.setStatus(SampleDict.S_RECEIVED);
                sample.setUpdatedAt(now);
                received++;
            }
            itemRepository.save(item);
            sampleRepository.save(sample);
        }

        // 部分异常也要保留已接收样品：批次 PARTIAL_RECEIVED；全部一致才 RECEIVED
        boolean allReceived = abnormal == 0;
        batch.setStatus(allReceived ? SampleDict.B_RECEIVED : SampleDict.B_PARTIAL);
        batch.setReceivedCount(received);
        batch.setAbnormalCount(abnormal);
        batch.setReceiver(receiver);
        batch.setReceivedAt(now);
        batch.setReceiveNote(trim(request.getNote()));
        batch.setLockVersion(batch.getLockVersion() + 1);
        batchRepository.save(batch);

        addEvent(batch.getId(),
                allReceived ? SampleDict.EV_RECEIVE : SampleDict.EV_PARTIAL,
                batch.getStatus(), items.size(), received, abnormal, receiver,
                buildReceiveNote(trim(request.getNote()), received, abnormal), now);
        return detail(batch.getId());
    }

    // ---------------- 实验室：整批退回 ----------------

    @Transactional
    public Map<String, Object> returnBatch(Long id, BatchReturnRequest request) {
        SampleBatch batch = lockBatch(id, request.getExpectedVersion());
        requireInTransit(batch, "退回");
        String reason = trim(request.getReason());
        if (reason == null) {
            throw new BizException("整批退回必须填写退回原因，便于现场补齐手续后重新送检");
        }
        String operator = trim(request.getReceiver());
        LocalDateTime now = LocalDateTime.now();

        // 样品退回待送检、脱离批次可重新组批；明细标随批退回保留在本批次下留痕
        List<SampleBatchItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
        for (SampleBatchItem item : items) {
            CoreSample sample = sampleRepository.findForUpdate(item.getSampleId()).orElse(null);
            if (sample != null) {
                sample.setStatus(SampleDict.S_REGISTERED);
                sample.setBatchId(null);
                sample.setUpdatedAt(now);
                sampleRepository.save(sample);
            }
            item.setItemStatus(SampleDict.ITEM_RETURNED);
            item.setAbnormalNote(reason);
            itemRepository.save(item);
        }

        batch.setStatus(SampleDict.B_RETURNED);
        batch.setReturnReason(reason);
        batch.setReturnedAt(now);
        if (operator != null) {
            batch.setReceiver(operator);
        }
        batch.setLockVersion(batch.getLockVersion() + 1);
        batchRepository.save(batch);

        addEvent(batch.getId(), SampleDict.EV_RETURN, SampleDict.B_RETURNED, items.size(), 0, 0,
                operator, reason, now);
        return detail(batch.getId());
    }

    // ---------------- 内部规则 ----------------

    private SampleBatch getBatch(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new BizException("送检批次不存在：id=" + id));
    }

    /** 批次行锁 + 乐观锁：重复点击接收/退回时，后提交者收到 409 */
    private SampleBatch lockBatch(Long id, Integer expectedVersion) {
        SampleBatch batch = batchRepository.findForUpdate(id)
                .orElseThrow(() -> new BizException("送检批次不存在：id=" + id));
        if (expectedVersion != null && !expectedVersion.equals(batch.getLockVersion())) {
            throw new ConflictException("批次数据已被更新（当前版本号 " + batch.getLockVersion()
                    + "，你看到的是 " + expectedVersion + "），请刷新样品明细后重新操作，避免重复交接");
        }
        return batch;
    }

    /** 只有运输中的批次能被接收或退回；已接收 / 部分接收 / 已退回的重复点击一律拒绝 */
    private void requireInTransit(SampleBatch batch, String action) {
        if (!SampleDict.B_IN_TRANSIT.equals(batch.getStatus())) {
            throw new BizException("批次【" + batch.getBatchNo() + "】当前为「"
                    + SampleDict.batchStatusName(batch.getStatus()) + "」，不能重复" + action
                    + "；历史交接结果可在批次交接记录中查看");
        }
    }

    /**
     * 批次编号：SJ-yyyyMMdd-当日两位序号。解析当天已有最大序号 + 1，
     * 极端并发下若编号已被占用则顺延，batch_no 唯一索引最终兜底。
     */
    private String generateBatchNo(LocalDate day) {
        String prefix = "SJ-" + day.format(DAY_FMT) + "-";
        long maxSeq = batchRepository.findFirstByBatchNoStartingWithOrderByBatchNoDesc(prefix)
                .map(b -> parseSeq(b.getBatchNo(), prefix)).orElse(0L);
        for (int delta = 1; delta <= 50; delta++) {
            String candidate = prefix + String.format("%02d", maxSeq + delta);
            if (!batchRepository.existsByBatchNo(candidate)) {
                return candidate;
            }
        }
        // 理论不可达：当天序号超过 50 或高并发争用时退化为毫秒后缀，仍保持唯一可读
        return prefix + String.format("%02d", maxSeq + 1) + "-" + (System.currentTimeMillis() % 1000);
    }

    private long parseSeq(String batchNo, String prefix) {
        try {
            return Long.parseLong(batchNo.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void addEvent(Long batchId, String type, String status, int sampleCount,
                          int received, int abnormal, String operator, String note, LocalDateTime at) {
        eventRepository.save(SampleBatchEvent.builder()
                .batchId(batchId)
                .eventType(type)
                .eventStatus(status)
                .sampleCount(sampleCount)
                .receivedCount(received)
                .abnormalCount(abnormal)
                .operator(operator)
                .note(note)
                .eventAt(at)
                .build());
    }

    private String buildReceiveNote(String note, int received, int abnormal) {
        String summary = "核对 " + (received + abnormal) + " 件：接收 " + received + " 件"
                + (abnormal > 0 ? "，异常待补 " + abnormal + " 件" : "，数量、封签全部一致");
        return note == null ? summary : summary + "；" + note;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
