package com.drill.crew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.drill.crew.common.BizException;
import com.drill.crew.common.ConflictException;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.domain.SampleBatch;
import com.drill.crew.domain.SampleBatchEvent;
import com.drill.crew.domain.SampleBatchItem;
import com.drill.crew.dto.BatchPackRequest;
import com.drill.crew.dto.BatchReceiveRequest;
import com.drill.crew.dto.BatchReturnRequest;
import com.drill.crew.dto.SamplePayload;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CoreSampleRepository;
import com.drill.crew.repository.SampleBatchEventRepository;
import com.drill.crew.service.SampleBatchService;
import com.drill.crew.service.SampleService;

/**
 * 岩芯样品取样与送检链路的运行级校验（H2 MySQL 兼容模式）。
 * 覆盖：草稿规则、孔段重叠、编号/封签唯一、封样发运、全部/部分接收、整批退回、
 * 重复操作防重、乐观锁冲突、退回后重新组批、交接事件可追溯。
 */
@SpringBootTest
@ActiveProfiles("test")
class SampleChainIntegrationTest {

    @Autowired
    private SampleService sampleService;
    @Autowired
    private SampleBatchService batchService;
    @Autowired
    private BoreholeRepository boreholeRepository;
    @Autowired
    private CoreSampleRepository sampleRepository;
    @Autowired
    private SampleBatchEventRepository eventRepository;

    private Long holeA;
    private Long holeB;

    @BeforeEach
    void setUp() {
        sampleRepository.deleteAll();
        boreholeRepository.deleteAll();
        Borehole a = boreholeRepository.save(Borehole.builder()
                .mineId(1L).holeNo("ZK-T-A").coordX(new BigDecimal("100.00")).coordY(new BigDecimal("200.00"))
                .designDepth(new BigDecimal("600.00")).actualDepth(new BigDecimal("500.00"))
                .status("施工").build());
        Borehole b = boreholeRepository.save(Borehole.builder()
                .mineId(1L).holeNo("ZK-T-B").coordX(new BigDecimal("300.00")).coordY(new BigDecimal("400.00"))
                .designDepth(new BigDecimal("600.00")).actualDepth(new BigDecimal("500.00"))
                .status("施工").build());
        holeA = a.getId();
        holeB = b.getId();
    }

    private SamplePayload payload(String no, String from, String to, String sampler, String seal) {
        SamplePayload p = new SamplePayload();
        p.setBoreholeId(holeA);
        p.setSampleNo(no);
        p.setFromDepth(from == null ? null : new BigDecimal(from));
        p.setToDepth(to == null ? null : new BigDecimal(to));
        p.setSampler(sampler);
        p.setSealNo(seal);
        p.setSampleType("岩芯");
        return p;
    }

    // ---------------- 1. 草稿规则 ----------------

    @Test
    void missingKeyInfoOnlySavesDraft() {
        // 缺取样人
        CoreSample s1 = sampleService.create(payload("HQ-T-01", "10.00", "12.00", null, "FS-T-01"));
        assertEquals(SampleDict.S_DRAFT, s1.getStatus());
        assertNull(s1.getRegisteredAt());

        // 缺样品编号与封签（深度倒挂在草稿下也允许保存，但两者都填时仍校验倒挂）
        SamplePayload noNoSeal = payload(null, "20.00", "22.00", "张三", null);
        CoreSample s2 = sampleService.create(noNoSeal);
        assertEquals(SampleDict.S_DRAFT, s2.getStatus());

        // 草稿不能组批
        assertTrue(sampleService.listPackable(holeA).isEmpty());

        // 补全后转正式
        SamplePayload completed = payload("HQ-T-01", "10.00", "12.00", "张三", "FS-T-01");
        completed.setExpectedVersion(s1.getLockVersion());
        CoreSample updated = sampleService.update(s1.getId(), completed);
        assertEquals(SampleDict.S_REGISTERED, updated.getStatus());
        assertNotNull(updated.getRegisteredAt());
    }

    @Test
    void draftWithBothDepthStillValidatesOrder() {
        SamplePayload bad = payload("HQ-X", "30.00", "20.00", null, null);
        assertThrows(BizException.class, () -> sampleService.create(bad));
    }

    // ---------------- 2. 唯一与重叠 ----------------

    @Test
    void duplicateNoAndSealRejected() {
        sampleService.create(payload("HQ-T-01", "10.00", "12.00", "张三", "FS-T-01"));
        assertThrows(BizException.class,
                () -> sampleService.create(payload("HQ-T-01", "20.00", "22.00", "李四", "FS-T-02")));
        assertThrows(BizException.class,
                () -> sampleService.create(payload("HQ-T-02", "20.00", "22.00", "李四", "FS-T-01")));
    }

    @Test
    void overlappingIntervalSameHoleRejected() {
        sampleService.create(payload("HQ-T-01", "10.00", "20.00", "张三", "FS-T-01"));
        // 内含重叠
        assertThrows(BizException.class,
                () -> sampleService.create(payload("HQ-T-02", "15.00", "16.00", "李四", "FS-T-02")));
        // 交叉重叠
        assertThrows(BizException.class,
                () -> sampleService.create(payload("HQ-T-03", "18.00", "25.00", "李四", "FS-T-03")));
        // 首尾相接允许
        CoreSample adjacent = sampleService.create(payload("HQ-T-04", "20.00", "25.00", "李四", "FS-T-04"));
        assertEquals(SampleDict.S_REGISTERED, adjacent.getStatus());
    }

    @Test
    void overlapAllowedOnDifferentHole() {
        sampleService.create(payload("HQ-T-01", "10.00", "20.00", "张三", "FS-T-01"));
        SamplePayload p = payload("HQ-T-05", "10.00", "20.00", "王五", "FS-T-05");
        p.setBoreholeId(holeB);
        CoreSample s = sampleService.create(p);
        assertEquals(SampleDict.S_REGISTERED, s.getStatus());
    }

    @Test
    void toDepthBeyondHoleRejected() {
        assertThrows(BizException.class,
                () -> sampleService.create(payload("HQ-T-09", "490.00", "520.00", "张三", "FS-T-09")));
    }

    // ---------------- 3. 封样发运 ----------------

    private CoreSample register(String no, String from, String to, String seal) {
        return sampleService.create(payload(no, from, to, "张三", seal));
    }

    private BatchPackRequest packRequest(Long... ids) {
        BatchPackRequest req = new BatchPackRequest();
        req.setSampleIds(List.of(ids));
        req.setPacker("封样员");
        req.setCarrier("送样司机");
        req.setNote("木箱铅封");
        return req;
    }

    @Test
    void packCreatesBatchItemsAndEvent() {
        CoreSample s1 = register("HQ-P-01", "10.00", "12.00", "FS-P-01");
        CoreSample s2 = register("HQ-P-02", "30.00", "32.00", "FS-P-02");

        Map<String, Object> detail = batchService.pack(packRequest(s1.getId(), s2.getId()));
        SampleBatch batch = (SampleBatch) detail.get("batch");
        @SuppressWarnings("unchecked")
        List<SampleBatchItem> items = (List<SampleBatchItem>) detail.get("items");
        @SuppressWarnings("unchecked")
        List<SampleBatchEvent> events = (List<SampleBatchEvent>) detail.get("events");

        assertEquals(SampleDict.B_IN_TRANSIT, batch.getStatus());
        assertEquals(2, batch.getExpectedCount());
        assertTrue(batch.getBatchNo().startsWith("SJ-"));
        assertEquals(2, items.size());
        assertEquals(SampleDict.ITEM_PENDING, items.get(0).getItemStatus());
        assertEquals(1, events.size());
        assertEquals(SampleDict.EV_PACK, events.get(0).getEventType());

        // 样品进入运输中并挂批次
        CoreSample reloaded = sampleService.get(s1.getId());
        assertEquals(SampleDict.S_IN_TRANSIT, reloaded.getStatus());
        assertEquals(batch.getId(), reloaded.getBatchId());

        // 运输中的样品不能现场改删
        SamplePayload edit = payload("HQ-P-01", "10.00", "12.00", "张三", "FS-P-01");
        edit.setExpectedVersion(reloaded.getLockVersion());
        assertThrows(BizException.class, () -> sampleService.update(s1.getId(), edit));

        // 重复封样同样品被拒绝
        assertThrows(BizException.class, () -> batchService.pack(packRequest(s1.getId(), s2.getId())));
    }

    @Test
    void packRejectsDraftAndDuplicatedSelection() {
        CoreSample draft = sampleService.create(payload(null, "10.00", "12.00", null, null));
        assertThrows(BizException.class, () -> batchService.pack(packRequest(draft.getId())));

        CoreSample s = register("HQ-P-09", "10.00", "12.00", "FS-P-09");
        BatchPackRequest dup = new BatchPackRequest();
        dup.setSampleIds(List.of(s.getId(), s.getId()));
        dup.setPacker("封样员");
        assertThrows(BizException.class, () -> batchService.pack(dup));
    }

    // ---------------- 4. 实验室接收 ----------------

    private BatchReceiveRequest.Line line(long sampleId, boolean abnormal, String note) {
        BatchReceiveRequest.Line l = new BatchReceiveRequest.Line();
        l.setSampleId(sampleId);
        l.setAbnormal(abnormal);
        l.setAbnormalNote(note);
        return l;
    }

    private BatchReceiveRequest receiveRequest(Long batchId, List<BatchReceiveRequest.Line> lines, Integer lv) {
        BatchReceiveRequest req = new BatchReceiveRequest();
        req.setReceiver("实验室-吴敏");
        req.setNote("核对完毕");
        req.setItems(lines);
        req.setExpectedVersion(lv);
        return req;
    }

    @Test
    void receiveAllConsistent() {
        CoreSample s1 = register("HQ-R-01", "10.00", "12.00", "FS-R-01");
        CoreSample s2 = register("HQ-R-02", "30.00", "32.00", "FS-R-02");
        SampleBatch batch = (SampleBatch) batchService.pack(packRequest(s1.getId(), s2.getId())).get("batch");

        BatchReceiveRequest req = receiveRequest(batch.getId(),
                List.of(line(s1.getId(), false, null), line(s2.getId(), false, null)), 0);
        Map<String, Object> detail = batchService.receive(batch.getId(), req);
        SampleBatch done = (SampleBatch) detail.get("batch");

        assertEquals(SampleDict.B_RECEIVED, done.getStatus());
        assertEquals(2, done.getReceivedCount());
        assertEquals(0, done.getAbnormalCount());
        assertNotNull(done.getReceivedAt());
        assertEquals("实验室-吴敏", done.getReceiver());

        @SuppressWarnings("unchecked")
        List<SampleBatchItem> items = (List<SampleBatchItem>) detail.get("items");
        items.forEach(i -> {
            assertEquals(SampleDict.ITEM_RECEIVED, i.getItemStatus());
            assertNotNull(i.getReceivedAt());
        });
        assertEquals(SampleDict.S_RECEIVED, sampleService.get(s1.getId()).getStatus());

        // 历史交接事件：封样 + 接收
        List<SampleBatchEvent> events = eventRepository.findByBatchIdOrderByEventAtAscIdAsc(batch.getId());
        assertEquals(2, events.size());
        assertEquals(SampleDict.EV_PACK, events.get(0).getEventType());
        assertEquals(SampleDict.EV_RECEIVE, events.get(1).getEventType());

        // 重复接收不能产生重复记录：批次已非运输中（乐观锁 0 也已过期）
        BatchReceiveRequest again = receiveRequest(batch.getId(),
                List.of(line(s1.getId(), false, null), line(s2.getId(), false, null)), 0);
        assertThrows(ConflictException.class, () -> batchService.receive(batch.getId(), again));
    }

    @Test
    void partialReceiveKeepsAcceptedAndMarksAbnormal() {
        CoreSample good = register("HQ-R-11", "10.00", "12.00", "FS-R-11");
        CoreSample bad = register("HQ-R-12", "30.00", "32.00", "FS-R-12");
        SampleBatch batch = (SampleBatch) batchService.pack(packRequest(good.getId(), bad.getId())).get("batch");

        // 异常行不填说明必须拒绝
        BatchReceiveRequest noNote = receiveRequest(batch.getId(),
                List.of(line(good.getId(), false, null), line(bad.getId(), true, null)), 0);
        assertThrows(BizException.class, () -> batchService.receive(batch.getId(), noNote));

        BatchReceiveRequest req = receiveRequest(batch.getId(),
                List.of(line(good.getId(), false, null),
                        line(bad.getId(), true, "封签破损，样品可辨，待补封签")), 0);
        Map<String, Object> detail = batchService.receive(batch.getId(), req);
        SampleBatch done = (SampleBatch) detail.get("batch");

        assertEquals(SampleDict.B_PARTIAL, done.getStatus());
        assertEquals(1, done.getReceivedCount());
        assertEquals(1, done.getAbnormalCount());

        // 已接收样品保留；异常样品标待补
        assertEquals(SampleDict.S_RECEIVED, sampleService.get(good.getId()).getStatus());
        assertEquals(SampleDict.S_ABNORMAL, sampleService.get(bad.getId()).getStatus());

        @SuppressWarnings("unchecked")
        List<SampleBatchItem> items = (List<SampleBatchItem>) detail.get("items");
        SampleBatchItem badItem = items.stream().filter(i -> i.getSampleId().equals(bad.getId())).findFirst().orElseThrow();
        assertEquals(SampleDict.ITEM_ABNORMAL, badItem.getItemStatus());
        assertTrue(badItem.getAbnormalNote().contains("封签破损"));

        @SuppressWarnings("unchecked")
        List<SampleBatchEvent> events = (List<SampleBatchEvent>) detail.get("events");
        assertEquals(SampleDict.EV_PARTIAL, events.get(events.size() - 1).getEventType());

        // 部分接收后的批次也不能再重复接收
        assertThrows(ConflictException.class,
                () -> batchService.receive(batch.getId(), req));
    }

    @Test
    void receiveRejectsCountMismatchAndUnknownSample() {
        CoreSample s1 = register("HQ-R-21", "10.00", "12.00", "FS-R-21");
        CoreSample s2 = register("HQ-R-22", "30.00", "32.00", "FS-R-22");
        SampleBatch batch = (SampleBatch) batchService.pack(packRequest(s1.getId(), s2.getId())).get("batch");

        // 少核对 1 件：数量不一致
        BatchReceiveRequest missing = receiveRequest(batch.getId(),
                List.of(line(s1.getId(), false, null)), 0);
        assertThrows(BizException.class, () -> batchService.receive(batch.getId(), missing));

        // 多出不属于本批次的样品 id
        CoreSample outsider = register("HQ-R-23", "60.00", "62.00", "FS-R-23");
        BatchReceiveRequest extra = receiveRequest(batch.getId(),
                List.of(line(s1.getId(), false, null), line(s2.getId(), false, null),
                        line(outsider.getId(), false, null)), 0);
        assertThrows(BizException.class, () -> batchService.receive(batch.getId(), extra));
    }

    // ---------------- 5. 整批退回与重新组批 ----------------

    @Test
    void wholeBatchReturnThenRepack() {
        CoreSample s1 = register("HQ-U-01", "10.00", "12.00", "FS-U-01");
        SampleBatch batch = (SampleBatch) batchService.pack(packRequest(s1.getId())).get("batch");

        // 退回原因必填
        BatchReturnRequest noReason = new BatchReturnRequest();
        noReason.setReceiver("实验室-吴敏");
        noReason.setExpectedVersion(0);
        assertThrows(BizException.class, () -> batchService.returnBatch(batch.getId(), noReason));

        BatchReturnRequest req = new BatchReturnRequest();
        req.setReceiver("实验室-吴敏");
        req.setReason("送样单缺失，来源无法核对");
        req.setExpectedVersion(0);
        Map<String, Object> detail = batchService.returnBatch(batch.getId(), req);
        SampleBatch returned = (SampleBatch) detail.get("batch");

        assertEquals(SampleDict.B_RETURNED, returned.getStatus());
        assertNotNull(returned.getReturnedAt());

        // 样品回到待送检、脱离批次，可重新组批
        CoreSample back = sampleService.get(s1.getId());
        assertEquals(SampleDict.S_REGISTERED, back.getStatus());
        assertNull(back.getBatchId());
        assertEquals(1, sampleService.listPackable(holeA).size());

        @SuppressWarnings("unchecked")
        List<SampleBatchItem> items = (List<SampleBatchItem>) detail.get("items");
        assertEquals(SampleDict.ITEM_RETURNED, items.get(0).getItemStatus());

        // 退回事件留痕
        List<SampleBatchEvent> events = eventRepository.findByBatchIdOrderByEventAtAscIdAsc(batch.getId());
        assertEquals(SampleDict.EV_RETURN, events.get(events.size() - 1).getEventType());

        // 已退回批次不能再重复退回 / 接收
        assertThrows(ConflictException.class, () -> batchService.returnBatch(batch.getId(), req));
        BatchReceiveRequest receive = receiveRequest(batch.getId(),
                List.of(line(s1.getId(), false, null)), returned.getLockVersion());
        assertThrows(BizException.class, () -> batchService.receive(batch.getId(), receive));

        // 重新组批成功，且新批次号与旧批次不同
        SampleBatch repack = (SampleBatch) batchService.pack(packRequest(s1.getId())).get("batch");
        assertFalse(repack.getId().equals(batch.getId()));
        assertFalse(repack.getBatchNo().equals(batch.getBatchNo()));
    }

    // ---------------- 6. 样品乐观锁 ----------------

    @Test
    void staleSampleVersionRejected() {
        CoreSample s = register("HQ-L-01", "10.00", "12.00", "FS-L-01");
        // 第一次更新成功（lv 0 -> 1）
        SamplePayload first = payload("HQ-L-01", "10.00", "12.50", "张三", "FS-L-01");
        first.setExpectedVersion(0);
        sampleService.update(s.getId(), first);

        // 仍持有旧版本号的第二次更新冲突
        SamplePayload stale = payload("HQ-L-01", "10.00", "13.00", "张三", "FS-L-01");
        stale.setExpectedVersion(0);
        assertThrows(ConflictException.class, () -> sampleService.update(s.getId(), stale));
    }
}
