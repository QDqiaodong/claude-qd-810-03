package com.drill.crew.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.common.ConflictException;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.dto.SamplePayload;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CoreSampleRepository;

/**
 * 岩芯样品现场取样登记。
 * <p>
 * 规则：
 * <ul>
 *   <li>关键信息（样品编号 / 起止深度 / 取样人 / 封签号）齐全 → REGISTERED 正式样品；
 *       缺任一项只能保存 DRAFT 草稿，草稿可继续补全后转正式；</li>
 *   <li>同孔正式样品孔段不得重叠（首尾相接允许）；草稿不参与重叠校验；</li>
 *   <li>样品编号、封签号全局唯一（唯一索引兜底）；</li>
 *   <li>已加入批次（运输中 / 已接收 / 待补）的样品现场不能再改删，整批退回后方可；</li>
 *   <li>lockVersion 乐观锁 + 行锁：重复点击保存后提交者收到 409，不产生重复/覆盖。</li>
 * </ul>
 */
@Service
public class SampleService {

    private final CoreSampleRepository sampleRepository;
    private final BoreholeRepository boreholeRepository;

    public SampleService(CoreSampleRepository sampleRepository, BoreholeRepository boreholeRepository) {
        this.sampleRepository = sampleRepository;
        this.boreholeRepository = boreholeRepository;
    }

    // ---------------- 查询 ----------------

    /** 取样登记台账：可按钻孔、状态筛选 */
    @Transactional(readOnly = true)
    public List<CoreSample> list(Long boreholeId, String status) {
        boolean hasHole = boreholeId != null;
        boolean hasStatus = status != null && !status.isBlank();
        List<CoreSample> list;
        if (hasHole && hasStatus) {
            list = sampleRepository.findByBoreholeIdAndStatusOrderByIdAsc(boreholeId, status);
        } else if (hasHole) {
            list = sampleRepository.findByBoreholeIdOrderByStatusAscFromDepthAscIdAsc(boreholeId);
        } else if (hasStatus) {
            list = sampleRepository.findByStatusOrderByIdAsc(status);
        } else {
            list = sampleRepository.findAll();
        }
        return list;
    }

    /** 待送检散样（REGISTERED）：组批选择器使用，可按钻孔过滤 */
    @Transactional(readOnly = true)
    public List<CoreSample> listPackable(Long boreholeId) {
        List<CoreSample> list = sampleRepository.findByStatusOrderByIdAsc(SampleDict.S_REGISTERED);
        if (boreholeId != null) {
            list.removeIf(s -> !boreholeId.equals(s.getBoreholeId()));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public CoreSample get(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> new BizException("样品不存在：id=" + id));
    }

    // ---------------- 新增 / 修改 ----------------

    @Transactional
    public CoreSample save(SamplePayload payload) {
        return create(payload);
    }

    @Transactional
    public CoreSample create(SamplePayload payload) {
        validateBorehole(payload.getBoreholeId());
        CoreSample sample = CoreSample.builder()
                .boreholeId(payload.getBoreholeId())
                .status(SampleDict.S_DRAFT)
                .lockVersion(0)
                .createdAt(LocalDateTime.now())
                .build();
        applyPayload(sample, payload);
        normalizeAndValidate(sample, true);
        sample.setUpdatedAt(LocalDateTime.now());
        return persist(sample);
    }

    @Transactional
    public CoreSample update(Long id, SamplePayload payload) {
        CoreSample sample = sampleRepository.findForUpdate(id)
                .orElseThrow(() -> new BizException("样品不存在：id=" + id));
        checkLock(sample, payload.getExpectedVersion());
        requireEditable(sample, "修改");
        if (payload.getBoreholeId() != null && !payload.getBoreholeId().equals(sample.getBoreholeId())) {
            validateBorehole(payload.getBoreholeId());
            sample.setBoreholeId(payload.getBoreholeId());
        }
        applyPayload(sample, payload);
        normalizeAndValidate(sample, false);
        sample.setUpdatedAt(LocalDateTime.now());
        return persist(sample);
    }

    @Transactional
    public void delete(Long id, Integer expectedVersion) {
        CoreSample sample = sampleRepository.findForUpdate(id)
                .orElseThrow(() -> new BizException("样品不存在：id=" + id));
        checkLock(sample, expectedVersion);
        requireEditable(sample, "删除");
        sampleRepository.delete(sample);
    }

    // ---------------- 业务规则 ----------------

    private void applyPayload(CoreSample sample, SamplePayload p) {
        sample.setSampleNo(trim(p.getSampleNo()));
        sample.setFromDepth(p.getFromDepth());
        sample.setToDepth(p.getToDepth());
        sample.setSampler(trim(p.getSampler()));
        sample.setSealNo(trim(p.getSealNo()));
        sample.setSampleType(trim(p.getSampleType()));
        sample.setRemark(trim(p.getRemark()));
    }

    /**
     * 归一化并校验：关键信息齐全 → 正式样品 REGISTERED；缺关键信息 → 草稿 DRAFT。
     */
    private void normalizeAndValidate(CoreSample sample, boolean isCreate) {
        boolean complete = sample.getSampleNo() != null
                && sample.getFromDepth() != null
                && sample.getToDepth() != null
                && sample.getSampler() != null
                && sample.getSealNo() != null;

        if (!complete) {
            // 缺关键信息：只能保存草稿；深度若都填了则顺带做倒挂校验，避免草稿留下明显错误
            if (sample.getFromDepth() != null && sample.getToDepth() != null) {
                validateDepthOrder(sample);
            }
            sample.setStatus(SampleDict.S_DRAFT);
            sample.setRegisteredAt(null);
            return;
        }

        // 正式样品完整校验
        validateDepthOrder(sample);
        if (sample.getFromDepth().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("起始深度不能为负数");
        }
        Borehole hole = boreholeRepository.findById(sample.getBoreholeId())
                .orElseThrow(() -> new BizException("钻孔不存在：id=" + sample.getBoreholeId()));
        if (sample.getToDepth().compareTo(hole.getActualDepth()) > 0) {
            throw new BizException("样品止深 " + sample.getToDepth() + " m 超过钻孔【" + hole.getHoleNo()
                    + "】当前孔深 " + hole.getActualDepth() + " m");
        }

        // 编号 / 封签唯一
        if (isCreate) {
            if (sampleRepository.existsBySampleNo(sample.getSampleNo())) {
                throw new BizException("样品编号【" + sample.getSampleNo() + "】已存在，样品编号必须唯一");
            }
            if (sampleRepository.existsBySealNo(sample.getSealNo())) {
                throw new BizException("封签号【" + sample.getSealNo() + "】已存在，封签号必须唯一");
            }
        } else {
            if (sampleRepository.existsBySampleNoAndIdNot(sample.getSampleNo(), sample.getId())) {
                throw new BizException("样品编号【" + sample.getSampleNo() + "】已存在，样品编号必须唯一");
            }
            if (sampleRepository.existsBySealNoAndIdNot(sample.getSealNo(), sample.getId())) {
                throw new BizException("封签号【" + sample.getSealNo() + "】已存在，封签号必须唯一");
            }
        }

        // 同孔正式样品孔段不得重叠（首尾相接允许）；草稿不参与
        validateNoOverlap(sample);

        boolean wasDraft = SampleDict.S_DRAFT.equals(sample.getStatus());
        sample.setStatus(SampleDict.S_REGISTERED);
        if (wasDraft || sample.getRegisteredAt() == null) {
            sample.setRegisteredAt(LocalDateTime.now());
        }
    }

    private void validateDepthOrder(CoreSample sample) {
        if (sample.getToDepth().compareTo(sample.getFromDepth()) <= 0) {
            throw new BizException("样品起止深度倒挂：起始 " + sample.getFromDepth() + " m 必须小于终止 "
                    + sample.getToDepth() + " m");
        }
    }

    /**
     * 同孔孔段重叠校验：newStart &lt; existEnd 且 existStart &lt; newEnd 即判为重叠。
     * 只与同孔正式样品（非草稿）比较；边界相接（newStart = existEnd）不算重叠。
     */
    private void validateNoOverlap(CoreSample sample) {
        List<CoreSample> siblings = sampleRepository
                .findByBoreholeIdAndStatusNotOrderByFromDepthAscIdAsc(sample.getBoreholeId(), SampleDict.S_DRAFT);
        for (CoreSample other : siblings) {
            if (other.getId() != null && other.getId().equals(sample.getId())) {
                continue;
            }
            boolean overlap = sample.getFromDepth().compareTo(other.getToDepth()) < 0
                    && other.getFromDepth().compareTo(sample.getToDepth()) < 0;
            if (overlap) {
                throw new BizException("孔段 " + sample.getFromDepth() + "~" + sample.getToDepth()
                        + " m 与已登记样品【" + other.getSampleNo() + "】（" + other.getFromDepth() + "~"
                        + other.getToDepth() + " m）重叠，同一孔段不能生成重叠样品");
            }
        }
    }

    private Borehole validateBorehole(Long boreholeId) {
        if (boreholeId == null) {
            throw new BizException("样品必须登记在指定孔段下，请先选择钻孔");
        }
        return boreholeRepository.findById(boreholeId)
                .orElseThrow(() -> new BizException("钻孔不存在：id=" + boreholeId));
    }

    /** 已封样（在批次中）的样品不能在现场改删；草稿与待送检、整批退回后的可以 */
    private void requireEditable(CoreSample sample, String action) {
        String st = sample.getStatus();
        if (SampleDict.S_IN_TRANSIT.equals(st) || SampleDict.S_RECEIVED.equals(st)
                || SampleDict.S_ABNORMAL.equals(st)) {
            throw new BizException("样品【" + sample.getSampleNo() + "】已随批次"
                    + (SampleDict.S_IN_TRANSIT.equals(st) ? "发运" : "接收")
                    + "，不能" + action + "；若实验室整批退回，样品回到待送检后才可修改");
        }
    }

    private void checkLock(CoreSample sample, Integer expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(sample.getLockVersion())) {
            throw new ConflictException("样品数据已被更新（当前版本号 " + sample.getLockVersion()
                    + "，你看到的是 " + expectedVersion + "），请刷新后重新操作，避免重复保存");
        }
        sample.setLockVersion(sample.getLockVersion() + 1);
    }

    private CoreSample persist(CoreSample sample) {
        try {
            return sampleRepository.saveAndFlush(sample);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BizException("样品编号或封签号与已有记录冲突，编号和封签号必须唯一");
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
