package com.drill.crew.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.common.HandoverDict;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.Handover;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.HandoverRepository;

/**
 * 交接资料冻结守卫。
 * <p>
 * 【只加交接页面不算完成】——所有现有录入入口（回次 / 岩性层 / 参数时序 / 班报，
 * 以及钻孔实际孔深）在新增、修改、删除前都必须调用这里：
 * 只要钻孔存在「已接收」的生效交接版本，一律拒绝，必须由负责人填写原因返开后才能继续录入。
 */
@Service
public class HandoverLockService {

    private final HandoverRepository handoverRepository;
    private final BoreholeRepository boreholeRepository;

    public HandoverLockService(HandoverRepository handoverRepository, BoreholeRepository boreholeRepository) {
        this.handoverRepository = handoverRepository;
        this.boreholeRepository = boreholeRepository;
    }

    /** 钻孔的当前生效交接版本（可能为空） */
    @Transactional(readOnly = true)
    public Handover activeHandover(Long boreholeId) {
        if (boreholeId == null) {
            return null;
        }
        return handoverRepository.findByBoreholeIdAndActiveFlag(boreholeId, 1).orElse(null);
    }

    /**
     * 录入入口守卫：已接收（且未返开）时禁止写入。
     *
     * @param action 正在执行的动作名，拼进错误提示，如「新增回次」「删除班报」
     */
    @Transactional(readOnly = true)
    public void requireWritable(Long boreholeId, String action) {
        Handover active = activeHandover(boreholeId);
        if (active != null && HandoverDict.ACCEPTED.equals(active.getStatus())) {
            String holeNo = boreholeNo(boreholeId);
            throw new BizException("钻孔【" + holeNo + "】的终孔交接资料已于 " + active.getCheckedAt()
                    + " 确认接收并冻结，不能" + action + "；确需补录请由地质负责人在交接台填写返开原因后再操作");
        }
    }

    /** 删除钻孔守卫：已产生任何交接记录（含历史版本）的钻孔不允许删除 */
    @Transactional(readOnly = true)
    public void requireDeletable(Long boreholeId) {
        if (boreholeId != null && handoverRepository.countByBoreholeId(boreholeId) > 0) {
            throw new BizException("钻孔【" + boreholeNo(boreholeId) + "】已存在终孔交接记录（含历史版本），不能删除");
        }
    }

    private String boreholeNo(Long boreholeId) {
        Borehole hole = boreholeId == null ? null
                : boreholeRepository.findById(boreholeId).orElse(null);
        return hole == null ? String.valueOf(boreholeId) : hole.getHoleNo();
    }
}
