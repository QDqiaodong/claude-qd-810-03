package com.drill.crew.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.drill.crew.domain.CoreRound;
import com.drill.crew.domain.DrillParamSeries;
import com.drill.crew.domain.LithoLayer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 交接发起 / 重新提交瞬间的【资料快照】。
 * 以当时的实际孔深定稿，整体存入 handover.snapshot（JSON 列）；
 * 历史版本永远还原的是这一份数据，后续录入入口对原资料的改写不会影响它。
 * <p>
 * 班报另用 {@link SnapshotReport}（脱离 JPA 托管的纯 POJO，避免懒加载 rig 代理进入 JSON），
 * 其余三类直接复用无导航属性的实体。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoverSnapshot {

    /** 钻孔信息快照（只含交接关心的字段） */
    private HoleSnapshot hole;

    /** 发起当时的实际孔深 m，与 hole.actualDepth 一致，单独冗余便于列表展示 */
    private BigDecimal actualDepth;

    /** 回次快照 */
    private List<CoreRound> rounds;

    /** 岩性层快照 */
    private List<LithoLayer> layers;

    /** 钻进参数时序快照（含 JSON points） */
    private List<DrillParamSeries> paramSeries;

    /** 班报快照（含 JSON materials） */
    private List<SnapshotReport> reports;

    /** 快照生成时的资料数量，供完整性概览展示 */
    private int roundCount;
    private int layerCount;
    private int seriesCount;
    private int reportCount;

    /**
     * 钻孔信息快照（脱离 JPA 托管，避免 mine 懒加载代理被写进快照 JSON）。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoleSnapshot {
        private Long id;
        private String holeNo;
        private Long mineId;
        private Long rigId;
        private BigDecimal designDepth;
        private BigDecimal actualDepth;
        private LocalDate spudDate;
        private LocalDate finalDate;
        private BigDecimal dipAngle;
        private String status;
    }
}
