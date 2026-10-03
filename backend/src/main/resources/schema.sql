-- =====================================================================
-- 地质钻探队 · 钻机与岩芯编录管理   drill_crew
-- MySQL 8 原生 json 列：litho_layer.attrs / drill_param_series.points
--                      shift_report.materials / rig.specs
-- 初始化脚本在【构建期】由 mysql/Dockerfile 执行一次，容器启动不再跑。
-- =====================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS drill_crew
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE drill_crew;

DROP TABLE IF EXISTS sample_batch_event;
DROP TABLE IF EXISTS sample_batch_item;
DROP TABLE IF EXISTS sample_batch;
DROP TABLE IF EXISTS core_sample;
DROP TABLE IF EXISTS handover_issue;
DROP TABLE IF EXISTS handover;
DROP TABLE IF EXISTS shift_report;
DROP TABLE IF EXISTS drill_param_series;
DROP TABLE IF EXISTS litho_layer;
DROP TABLE IF EXISTS core_round;
DROP TABLE IF EXISTS crew_team;
DROP TABLE IF EXISTS borehole;
DROP TABLE IF EXISTS rig;
DROP TABLE IF EXISTS mine_area;

-- ---------------------------- 1. 矿区 ----------------------------
CREATE TABLE mine_area (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  code            VARCHAR(32)  NOT NULL COMMENT '矿区编号',
  name            VARCHAR(64)  NOT NULL COMMENT '矿区名称',
  stage           VARCHAR(16)  NOT NULL COMMENT '勘查阶段 预查/普查/详查/勘探',
  location        VARCHAR(64)  DEFAULT NULL COMMENT '行政位置',
  area_km2        DECIMAL(10,2) DEFAULT NULL COMMENT '矿权面积 km2',
  chief_geologist VARCHAR(32)  DEFAULT NULL COMMENT '项目负责人',
  started_on      DATE         DEFAULT NULL COMMENT '进场日期',
  remark          VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mine_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 2. 钻机 ----------------------------
CREATE TABLE rig (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  code            VARCHAR(32)  NOT NULL COMMENT '钻机编号',
  model           VARCHAR(32)  NOT NULL COMMENT '钻机型号',
  max_depth       DECIMAL(8,2) NOT NULL COMMENT '最大钻深 m',
  engine_power    DECIMAL(8,2) NOT NULL COMMENT '发动机功率 kW',
  status          VARCHAR(16)  NOT NULL COMMENT '运行/搬迁/维修/停用',
  manufacturer    VARCHAR(64)  DEFAULT NULL COMMENT '生产厂家',
  commissioned_on DATE         DEFAULT NULL COMMENT '投产日期',
  specs           JSON         DEFAULT NULL COMMENT 'JSON 属性包：动力/卷扬/泥浆泵/钻杆等可变参数',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rig_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 3. 班组 ----------------------------
CREATE TABLE crew_team (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  rig_id       BIGINT      NOT NULL COMMENT '所属钻机',
  leader       VARCHAR(32) NOT NULL COMMENT '班组长',
  worker_count INT         NOT NULL DEFAULT 3 COMMENT '钻工人数',
  shift_type   VARCHAR(8)  NOT NULL COMMENT '值班班别 早/中/夜',
  phone        VARCHAR(24) DEFAULT NULL,
  note         VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_crew_rig (rig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 4. 钻孔 ----------------------------
CREATE TABLE borehole (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  mine_id      BIGINT       NOT NULL COMMENT '所属矿区',
  rig_id       BIGINT       DEFAULT NULL COMMENT '施工钻机',
  hole_no      VARCHAR(32)  NOT NULL COMMENT '孔号',
  coord_x      DECIMAL(12,2) NOT NULL COMMENT '坐标 X',
  coord_y      DECIMAL(14,2) NOT NULL COMMENT '坐标 Y',
  design_depth DECIMAL(8,2) NOT NULL COMMENT '设计孔深 m',
  actual_depth DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '实际孔深 m',
  spud_date    DATE         DEFAULT NULL COMMENT '开孔日期',
  final_date   DATE         DEFAULT NULL COMMENT '终孔日期',
  collar_elev  DECIMAL(8,2) DEFAULT NULL COMMENT '孔口标高 m',
  dip_angle    DECIMAL(5,2) DEFAULT NULL COMMENT '倾角 °',
  status       VARCHAR(16)  NOT NULL COMMENT '施工/终孔/封孔',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hole_no (hole_no),
  KEY idx_hole_mine (mine_id),
  KEY idx_hole_rig (rig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 5. 回次 ----------------------------
CREATE TABLE core_round (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  borehole_id        BIGINT       NOT NULL,
  round_no           INT          NOT NULL COMMENT '回次号',
  from_depth         DECIMAL(8,2) NOT NULL COMMENT '起始深度 m',
  to_depth           DECIMAL(8,2) NOT NULL COMMENT '终止深度 m',
  core_length        DECIMAL(8,2) NOT NULL COMMENT '岩芯长度 m',
  recovery_rate      DECIMAL(5,2) NOT NULL COMMENT '采取率 %',
  rqd                DECIMAL(5,2) NOT NULL COMMENT 'RQD %',
  driller            VARCHAR(32)  DEFAULT NULL COMMENT '记录员/班长',
  sampled_at         DATE         DEFAULT NULL COMMENT '编录日期',
  low_recovery_reason VARCHAR(32) DEFAULT NULL COMMENT '低采取率原因 岩芯破碎/漏失/孔径失控',
  remark             VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_round (borehole_id, round_no),
  KEY idx_round_hole (borehole_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 6. 岩性分层（核心，含 JSON attrs） ----------------------------
CREATE TABLE litho_layer (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  round_id    BIGINT       NOT NULL COMMENT '所属回次',
  borehole_id BIGINT       NOT NULL COMMENT '所属钻孔（冗余，便于按孔检索）',
  layer_seq   INT          NOT NULL COMMENT '层序',
  lithology   VARCHAR(32)  NOT NULL COMMENT '岩性名称',
  from_depth  DECIMAL(8,2) NOT NULL COMMENT '起深 m',
  to_depth    DECIMAL(8,2) NOT NULL COMMENT '止深 m',
  color       VARCHAR(24)  DEFAULT NULL COMMENT '颜色',
  weathering  VARCHAR(24)  DEFAULT NULL COMMENT '风化程度',
  structure   VARCHAR(32)  DEFAULT NULL COMMENT '结构构造',
  description VARCHAR(255) DEFAULT NULL COMMENT '描述',
  attrs       JSON         DEFAULT NULL COMMENT 'JSON 属性包：含水性/硬度/裂隙发育/矿化/蚀变/取样编号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_layer (round_id, layer_seq),
  KEY idx_layer_hole (borehole_id),
  KEY idx_layer_litho (lithology)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 7. 钻进参数时序（含 JSON points） ----------------------------
CREATE TABLE drill_param_series (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  borehole_id BIGINT      NOT NULL,
  rig_id      BIGINT      DEFAULT NULL,
  record_date DATE        NOT NULL COMMENT '记录日期',
  shift_type  VARCHAR(8)  NOT NULL COMMENT '班别',
  operator    VARCHAR(32) DEFAULT NULL COMMENT '记录人',
  points      JSON        NOT NULL COMMENT 'JSON 多点采样数组：钻压/转速/泵量/泥浆密度/孔内情况',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_param_hole (borehole_id),
  KEY idx_param_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 8. 班报表（含 JSON materials） ----------------------------
CREATE TABLE shift_report (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  borehole_id   BIGINT       NOT NULL,
  rig_id        BIGINT       NOT NULL,
  crew_id       BIGINT       DEFAULT NULL COMMENT '班组',
  report_date   DATE         NOT NULL COMMENT '日期',
  shift_type    VARCHAR(8)   NOT NULL COMMENT '班别 早/中/夜',
  footage       DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '进尺 m',
  pure_hours    DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '纯钻时间 h',
  aux_hours     DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '辅助时间 h',
  accident_note VARCHAR(255) DEFAULT NULL COMMENT '事故与停待记录',
  materials     JSON         DEFAULT NULL COMMENT 'JSON 材料消耗明细',
  handover_note VARCHAR(255) DEFAULT NULL COMMENT '交接班备注',
  leader_sign   VARCHAR(32)  DEFAULT NULL COMMENT '班组长签字',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_shift_rig (rig_id),
  KEY idx_shift_hole (borehole_id),
  KEY idx_shift_date (report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 9. 钻孔终孔交接（版本链） ----------------------------
-- 一个钻孔的一条交接线：每发起一次 / 整改后重新提交都新增一行（新版本）。
-- active_flag = 1 的行【每孔至多一条】（uk_handover_active），表示当前生效版本；
-- 历史版本与返开失效版本该列均为 NULL。
-- status：PENDING_CHECK 待检查 / NEED_FIX 待整改 / ACCEPTED 已接收 / SUPERSEDED 已失效
CREATE TABLE handover (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  borehole_id        BIGINT       NOT NULL COMMENT '交接钻孔',
  version_no         INT          NOT NULL COMMENT '交接版本号（同一钻孔递增）',
  status             VARCHAR(16)  NOT NULL COMMENT '待检查/待整改/已接收/已失效',
  active_flag        TINYINT      DEFAULT NULL COMMENT '是否当前生效版本：1=是，NULL=历史/失效',
  snapshot_depth     DECIMAL(8,2) NOT NULL COMMENT '发起/提交时的实际孔深快照 m',
  snapshot           JSON         NOT NULL COMMENT '资料快照：孔信息+回次+岩性层+参数时序+班报',
  issue_total        INT          NOT NULL DEFAULT 0 COMMENT '本版本问题项总数',
  issue_open         INT          NOT NULL DEFAULT 0 COMMENT '待处理问题数',
  submit_note        VARCHAR(255) DEFAULT NULL COMMENT '班组提交说明',
  initiated_by       VARCHAR(32)  DEFAULT NULL COMMENT '发起人（班组长）',
  initiated_at       DATETIME     DEFAULT NULL COMMENT '发起/重新提交时间',
  checker_name       VARCHAR(32)  DEFAULT NULL COMMENT '检查负责人',
  checked_at         DATETIME     DEFAULT NULL COMMENT '最近一次检查（退回/接收）时间',
  reject_reason      VARCHAR(255) DEFAULT NULL COMMENT '本轮退回原因',
  last_reject_reason VARCHAR(255) DEFAULT NULL COMMENT '上一轮退回原因（随新版本带出，可追溯）',
  reopen_reason      VARCHAR(255) DEFAULT NULL COMMENT '接收后返开原因（仅失效版本）',
  lock_version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁：两人同时处理时后提交者看到数据已变化',
  created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_handover_ver (borehole_id, version_no),
  UNIQUE KEY uk_handover_active (borehole_id, active_flag),
  KEY idx_handover_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 10. 交接问题项（随版本保存） ----------------------------
-- 问题项绑定到具体资料（target_type + target_id），点击可直接跳回对应录入页整改。
-- 回次/岩性层不连续不重叠、低采取率缺原因、参数异常、班报缺签字都会落为明确问题项。
CREATE TABLE handover_issue (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  handover_id  BIGINT       NOT NULL COMMENT '所属交接版本',
  issue_type   VARCHAR(22)  NOT NULL COMMENT 'ROUND_GAP/ROUND_LOW_RECOVERY/LAYER_GAP/PARAM_ABNORMAL/REPORT_UNSIGNED',
  target_type  VARCHAR(16)  NOT NULL COMMENT 'round/layer/series/report',
  target_id    BIGINT       DEFAULT NULL COMMENT '对应资料主键（连续性问题可为空）',
  target_label VARCHAR(128) DEFAULT NULL COMMENT '资料定位文本，如 第3回次 / 2024-08-11 早班',
  issue_key    VARCHAR(120) NOT NULL COMMENT '问题稳定标识：按当前资料重算后用于判定是否已消除',
  message      VARCHAR(255) NOT NULL COMMENT '问题描述',
  status       VARCHAR(10)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 待处理 / RESOLVED 已处理',
  PRIMARY KEY (id),
  KEY idx_issue_handover (handover_id),
  KEY idx_issue_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 11. 岩芯样品（现场取样登记） ----------------------------
-- 关键信息（样品编号/起止深度/取样人/封签号）齐全才能登记为 REGISTERED；
-- 缺关键信息只保存 DRAFT 草稿，草稿不参与孔段重叠校验、不能加入送检批次。
-- status：DRAFT 草稿 / REGISTERED 已登记待送检 / IN_TRANSIT 运输中 /
--         RECEIVED 实验室已接收 / ABNORMAL 部分接收时标出的待补样品
-- 同孔正式样品（非草稿）孔段不得重叠，由应用层校验 + 编号/封签唯一索引兜底。
CREATE TABLE core_sample (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  borehole_id   BIGINT       NOT NULL COMMENT '所属钻孔',
  sample_no     VARCHAR(40)  DEFAULT NULL COMMENT '样品编号（草稿可空，正式唯一）',
  from_depth    DECIMAL(8,2) DEFAULT NULL COMMENT '起始深度 m',
  to_depth      DECIMAL(8,2) DEFAULT NULL COMMENT '终止深度 m',
  sampler       VARCHAR(32)  DEFAULT NULL COMMENT '取样人',
  seal_no       VARCHAR(40)  DEFAULT NULL COMMENT '封签号（草稿可空，正式唯一）',
  sample_type   VARCHAR(24)  DEFAULT NULL COMMENT '样品类型 岩芯/岩屑/水样/矿化样',
  status        VARCHAR(12)  NOT NULL COMMENT 'DRAFT/REGISTERED/IN_BATCH/IN_TRANSIT/RECEIVED/RETURNED',
  remark        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  batch_id      BIGINT       DEFAULT NULL COMMENT '当前所在批次（历史事件在 sample_batch_event 可追溯）',
  registered_at DATETIME     DEFAULT NULL COMMENT '登记为正式样品时间',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '首次保存（草稿）时间',
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  lock_version  INT          NOT NULL DEFAULT 0 COMMENT '乐观锁：重复点击/并发保存后提交者看到数据已变化',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sample_no (sample_no),
  UNIQUE KEY uk_sample_seal (seal_no),
  KEY idx_sample_hole (borehole_id),
  KEY idx_sample_status (status),
  KEY idx_sample_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 12. 送检批次（封样运输 / 实验室接收） ----------------------------
-- 批次一旦创建即进入 IN_TRANSIT（封样运输），不可再向其中增删样品；
-- 实验室接收时逐样核对【状态正确 + 数量一致 + 封签一致】：
--   全部一致 → RECEIVED，样品置 RECEIVED；
--   存在异常 → PARTIAL_RECEIVED，一致样品照样接收（RECEIVED），异常样品标 ABNORMAL 待补；
--   整批退回 → RETURNED，样品退回 REGISTERED 可重新组批。
-- status：IN_TRANSIT 运输中 / RECEIVED 已接收 / PARTIAL_RECEIVED 部分接收 / RETURNED 已退回
CREATE TABLE sample_batch (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  batch_no        VARCHAR(40)  NOT NULL COMMENT '批次编号（自动生成，唯一）',
  status          VARCHAR(18)  NOT NULL COMMENT 'IN_TRANSIT/RECEIVED/PARTIAL_RECEIVED/RETURNED',
  expected_count  INT          NOT NULL COMMENT '封样时样品数量（接收时必须与实到数量核对）',
  received_count  INT          NOT NULL DEFAULT 0 COMMENT '已接收样品数',
  abnormal_count  INT          NOT NULL DEFAULT 0 COMMENT '异常样品数（待补项）',
  packer          VARCHAR(32)  DEFAULT NULL COMMENT '封样人',
  carrier         VARCHAR(32)  DEFAULT NULL COMMENT '运输/送样人',
  pack_note       VARCHAR(255) DEFAULT NULL COMMENT '封样运输说明',
  packed_at       DATETIME     DEFAULT NULL COMMENT '封样发运时间',
  receiver        VARCHAR(32)  DEFAULT NULL COMMENT '实验室接收人',
  received_at     DATETIME     DEFAULT NULL COMMENT '实验室接收时间（回写）',
  receive_note    VARCHAR(255) DEFAULT NULL COMMENT '接收总说明 / 异常说明',
  return_reason   VARCHAR(255) DEFAULT NULL COMMENT '整批退回原因',
  returned_at     DATETIME     DEFAULT NULL COMMENT '整批退回时间',
  lock_version    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁：防止重复交接/重复接收产生重复记录',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_no (batch_no),
  KEY idx_batch_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 13. 批次样品明细（封样时定稿的快照） ----------------------------
-- 每行是批次中的一个样品：封样时记录封签号、孔段快照，供实验室逐项核对；
-- 接收阶段回写每行接收结果（行级异常说明），部分接收时保留已接收行。
-- 同一批次内一个样品只出现一次；整批退回后样品可重新组批（新批次产生新明细行），
-- 旧批次的退回明细继续保留留痕，故唯一键为 (batch_id, sample_id)。
-- item_status：PENDING 待接收 / RECEIVED 已接收 / ABNORMAL 异常待补 / RETURNED 随批退回
CREATE TABLE sample_batch_item (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  batch_id          BIGINT       NOT NULL,
  sample_id         BIGINT       NOT NULL,
  sample_no         VARCHAR(40)  NOT NULL COMMENT '样品编号快照',
  borehole_id       BIGINT       NOT NULL COMMENT '钻孔（明细展示用）',
  from_depth        DECIMAL(8,2) NOT NULL COMMENT '起始深度快照 m',
  to_depth          DECIMAL(8,2) NOT NULL COMMENT '终止深度快照 m',
  seal_no          VARCHAR(40)  NOT NULL COMMENT '封签号快照（实验室核对依据）',
  item_status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RECEIVED/ABNORMAL/RETURNED',
  abnormal_note     VARCHAR(255) DEFAULT NULL COMMENT '行级异常说明（数量不符/封签破损/封签不符…）',
  received_at       DATETIME     DEFAULT NULL COMMENT '本行接收时间',
  receiver          VARCHAR(32)  DEFAULT NULL COMMENT '本行接收人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_item_batch_sample (batch_id, sample_id),
  KEY idx_item_sample (sample_id),
  KEY idx_item_batch (batch_id),
  KEY idx_item_status (item_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------- 14. 批次交接事件（历史交接结果可追溯） ----------------------------
-- 封样发运 / 整批退回 / 全部接收 / 部分接收 每发生一次追加一行，只增不改；
-- 批次明细与样品当前状态可能随后续处理变化，本表永久保留每次交接的结论与操作痕迹。
CREATE TABLE sample_batch_event (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  batch_id         BIGINT       NOT NULL,
  event_type       VARCHAR(18)  NOT NULL COMMENT 'PACK/RECEIVE/PARTIAL_RECEIVE/RETURN',
  event_status     VARCHAR(18)  NOT NULL COMMENT '事件发生后批次状态',
  sample_count     INT          NOT NULL DEFAULT 0 COMMENT '涉及样品数',
  received_count   INT          NOT NULL DEFAULT 0 COMMENT '其中已接收数',
  abnormal_count   INT          NOT NULL DEFAULT 0 COMMENT '其中异常数',
  operator         VARCHAR(32)  DEFAULT NULL COMMENT '操作人（封样人/接收人）',
  note             VARCHAR(255) DEFAULT NULL COMMENT '说明 / 退回原因 / 异常概述',
  event_at         DATETIME     NOT NULL COMMENT '事件时间',
  PRIMARY KEY (id),
  KEY idx_event_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 种子数据
-- =====================================================================

-- ---------------------------- 矿区（5） ----------------------------
INSERT INTO mine_area (code, name, stage, location, area_km2, chief_geologist, started_on, remark) VALUES
('MK-2401','红柳沟钼矿区','勘探','甘肃 · 酒泉',12.60,'王建国','2021-03-08','主矿体走向北东，已完成三期钻探'),
('MK-2402','黑石坡铜镍矿区','详查','新疆 · 哈密',8.40,'李文斌','2022-05-16','基性-超基性岩体，熔离型矿化'),
('MK-2403','青石崖金矿区','普查','青海 · 格尔木',5.20,'张海涛','2023-04-02','构造蚀变岩型，石英脉发育'),
('MK-2404','白杨沟铅锌矿区','预查','内蒙 · 赤峰',3.80,'赵国强','2023-09-20','激电异常区，首轮验证'),
('MK-2405','大石板铁矿区','普查','河北 · 承德',6.90,'孙志远','2024-03-11','沉积变质型磁铁矿');

-- ---------------------------- 钻机（6，含 JSON specs） ----------------------------
INSERT INTO rig (code, model, max_depth, engine_power, status, manufacturer, commissioned_on, specs) VALUES
('ZJ-01','XY-5',  1000.00,132.00,'运行','张家口探矿机械厂','2019-04-12',
 '{"动力形式":"柴油机组","卷扬提升力":"35 kN","泥浆泵型号":"BW-250","钻杆规格":"Φ50 mm","液压系统":"双泵双回路","累计台月":"62"}'),
('ZJ-02','XY-6B', 1500.00,160.00,'运行','张家口探矿机械厂','2020-07-03',
 '{"动力形式":"电动机组","卷扬提升力":"45 kN","泥浆泵型号":"BW-320","钻杆规格":"Φ60 mm","液压系统":"负载敏感","累计台月":"48"}'),
('ZJ-03','JDX-1500',1500.00,185.00,'搬迁','山东地质机械厂','2018-11-21',
 '{"动力形式":"柴油机组","卷扬提升力":"50 kN","泥浆泵型号":"BW-320","钻杆规格":"Φ71 mm","液压系统":"全液压","累计台月":"77"}'),
('ZJ-04','XY-4',  800.00, 110.00,'维修','张家口探矿机械厂','2017-06-09',
 '{"动力形式":"电动机组","卷扬提升力":"28 kN","泥浆泵型号":"BW-160","钻杆规格":"Φ42 mm","液压系统":"单泵","累计台月":"91"}'),
('ZJ-05','HXY-2000',2000.00,220.00,'运行','无锡钻探工具厂','2021-09-30',
 '{"动力形式":"柴油机组","卷扬提升力":"70 kN","泥浆泵型号":"BW-450","钻杆规格":"Φ89 mm","液压系统":"全液压双泵","累计台月":"26"}'),
('ZJ-06','XY-2B', 600.00, 90.00,'停用','张家口探矿机械厂','2015-03-18',
 '{"动力形式":"电动机组","卷扬提升力":"20 kN","泥浆泵型号":"BW-150","钻杆规格":"Φ42 mm","液压系统":"单泵","累计台月":"120"}');

-- ---------------------------- 班组（6） ----------------------------
INSERT INTO crew_team (rig_id, leader, worker_count, shift_type, phone, note) VALUES
(1,'刘铁柱',4,'早','138-4012-7781','擅长硬岩金刚石钻进'),
(1,'马三保',3,'中','139-4021-5533','熟练处理孔内漏失'),
(2,'周立军',4,'夜','137-5566-2210','夜班负责人，含安全员'),
(3,'陈大勇',5,'早','136-8899-3402','搬迁安装主力班组'),
(5,'杨树森',4,'中','150-2233-8817','深孔绳索取芯经验'),
(5,'黄志刚',3,'夜','188-6677-1204','新组建，正在跟班学习'),
(4,'侯德才',3,'中','139-7788-2160','浅孔岩芯钻探熟练'),
(6,'宋长林',3,'早','137-2244-6093','兼机修，负责设备保养');

-- ---------------------------- 钻孔（12） ----------------------------
INSERT INTO borehole (mine_id, rig_id, hole_no, coord_x, coord_y, design_depth, actual_depth, spud_date, final_date, collar_elev, dip_angle, status) VALUES
(1,1,'ZK-2401',4301250.00,38512400.00,600.00,520.50,'2024-03-18','2024-06-02',1862.40,90.00,'终孔'),
(1,1,'ZK-2402',4301180.00,38512650.00,600.00,486.00,'2024-04-05','2024-06-25',1859.10,90.00,'终孔'),
(1,2,'ZK-2403',4301320.00,38512200.00,700.00,312.80,'2024-07-10',NULL,       1871.60,85.00,'施工'),
(1,5,'ZK-2404',4301090.00,38512900.00,700.00, 68.50,'2024-08-02',NULL,       1855.30,90.00,'施工'),
(2,3,'ZK-3101',4682300.00,31425600.00,900.00,864.20,'2023-05-12','2023-11-08',1420.75,88.00,'封孔'),
(2,3,'ZK-3102',4682450.00,31425900.00,900.00,742.60,'2023-06-20','2023-12-15',1426.30,90.00,'终孔'),
(2,2,'ZK-3103',4682180.00,31425300.00,800.00,528.40,'2024-02-18','2024-05-30',1418.90,85.00,'终孔'),
(3,4,'ZK-5201',3987600.00,17642300.00,500.00,458.30,'2023-09-05','2024-01-12',3210.55,90.00,'终孔'),
(3,4,'ZK-5202',3987750.00,17642650.00,500.00,196.70,'2024-04-22',NULL,       3215.20,80.00,'施工'),
(4,2,'ZK-6401',4720150.00,20435800.00,400.00,268.90,'2024-05-08','2024-07-19',1128.40,90.00,'终孔'),
(5,5,'ZK-7201',4455800.00,20514200.00,450.00, 32.00,'2024-09-01',NULL,        865.70,90.00,'施工'),
(5,6,'ZK-7202',4455680.00,20514550.00,450.00,152.40,'2024-06-15','2024-08-08',861.30,75.00,'封孔');

-- ---------------------------- 回次（34） ----------------------------
-- ZK-2401（孔深 520.50）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(1,1,  0.00, 86.80, 85.20,98.16,86.00,'刘铁柱','2024-03-22',NULL,'开孔回次，覆盖层跟管钻进'),
(1,2, 86.80,173.60, 83.70,96.43,78.00,'刘铁柱','2024-03-28',NULL,'进入基岩，金刚石钻头'),
(1,3,173.60,260.40, 80.10,92.28,65.00,'马三保','2024-04-06',NULL,'安山岩段，钻进平稳'),
(1,4,260.40,347.20, 74.80,86.18,54.00,'马三保','2024-04-19',NULL,'钻遇破碎带，冲洗液漏失'),
(1,5,347.20,434.00, 68.30,78.69,44.00,'刘铁柱','2024-05-04',NULL,'蚀变强烈，岩芯呈块状'),
(1,6,434.00,520.50, 63.10,72.95,38.00,'马三保','2024-05-26','岩芯破碎','矿化带，岩芯破碎严重');
-- ZK-2402（486.00）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(2,1,  0.00,121.50,119.30,98.19,84.00,'刘铁柱','2024-04-12',NULL,'大理岩段，岩芯完整'),
(2,2,121.50,243.00,117.20,96.46,76.00,'刘铁柱','2024-04-25',NULL,'条带状大理岩'),
(2,3,243.00,364.50,111.40,91.69,63.00,'马三保','2024-05-14',NULL,'进入片麻岩，进尺放慢'),
(2,4,364.50,486.00,104.60,86.09,52.00,'马三保','2024-06-08',NULL,'风化加强，岩芯短柱状');
-- ZK-2403（312.80，施工中）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(3,1,  0.00,104.30,102.10,97.89,82.00,'周立军','2024-07-16',NULL,'砾岩段，孔壁较稳'),
(3,2,104.30,208.60, 99.40,95.30,71.00,'周立军','2024-07-29',NULL,'砂岩，泥浆护壁'),
(3,3,208.60,312.80, 92.70,88.96,58.00,'周立军','2024-08-11',NULL,'花岗岩，可钻性差');
-- ZK-2404（68.50，施工中）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(4,1,0.00,68.50,66.80,97.52,79.00,'杨树森','2024-08-06',NULL,'开孔，覆盖层');
-- ZK-3101（864.20，已封孔）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(5,1,  0.00,216.10,211.80,98.01,88.00,'陈大勇','2023-05-20',NULL,'深孔绳索取芯首回次'),
(5,2,216.10,432.20,208.60,96.53,77.00,'陈大勇','2023-07-02',NULL,'灰岩段，完整柱状'),
(5,3,432.20,648.30,198.40,91.81,66.00,'陈大勇','2023-08-24',NULL,'岩溶发育，多次漏失'),
(5,4,648.30,864.20,183.50,84.99,51.00,'陈大勇','2023-10-15',NULL,'进入花岗岩基底');
-- ZK-3102（742.60）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(6,1,  0.00,247.50,242.60,98.02,85.00,'陈大勇','2023-06-28',NULL,'砂岩段，进尺快'),
(6,2,247.50,495.00,238.10,96.20,74.00,'陈大勇','2023-08-16',NULL,'泥岩缩径，多次扫孔'),
(6,3,495.00,742.60,224.30,90.59,62.00,'陈大勇','2023-10-30',NULL,'下部砂岩，含水性增强');
-- ZK-3103（528.40）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(7,1,  0.00,176.10,172.50,97.96,83.00,'周立军','2024-02-24',NULL,'安山岩段'),
(7,2,176.10,352.20,168.20,95.51,72.00,'周立军','2024-03-19',NULL,'杏仁状构造发育'),
(7,3,352.20,528.40,158.40,89.90,60.00,'周立军','2024-04-30',NULL,'见矿化细脉');
-- ZK-5201（458.30）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(8,1,  0.00,152.80,149.60,97.91,87.00,'黄志刚','2023-09-14',NULL,'砂岩，岩芯完整'),
(8,2,152.80,305.60,145.30,95.09,75.00,'黄志刚','2023-10-22',NULL,'页岩段，易水化'),
(8,3,305.60,458.30,134.80,88.28,56.00,'黄志刚','2023-12-06',NULL,'石英脉密集段');
-- ZK-5202（196.70，施工中）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(9,1, 0.00, 98.40, 96.20,97.76,80.00,'黄志刚','2024-04-28',NULL,'覆盖层 + 风化壳'),
(9,2,98.40,196.70, 90.10,91.66,64.00,'黄志刚','2024-05-20',NULL,'花岗岩，钻头磨损快');
-- ZK-6401（268.90）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(10,1,  0.00,134.50,131.80,97.99,81.00,'马三保','2024-05-15',NULL,'片麻岩，取芯良好'),
(10,2,134.50,268.90,122.60,91.22,61.00,'马三保','2024-06-27',NULL,'见铅锌矿化细脉');
-- ZK-7201（32.00，施工中）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(11,1,0.00,32.00,31.40,98.13,74.00,'杨树森','2024-09-04',NULL,'开孔回次');
-- ZK-7202（152.40，已封孔）
INSERT INTO core_round (borehole_id, round_no, from_depth, to_depth, core_length, recovery_rate, rqd, driller, sampled_at, low_recovery_reason, remark) VALUES
(12,1, 0.00, 76.20, 74.50,97.77,78.00,'刘铁柱','2024-06-20',NULL,'砂岩段'),
(12,2,76.20,152.40, 68.30,89.63,55.00,'刘铁柱','2024-07-12',NULL,'泥岩，封孔前末回次');

-- ---------------------------- 岩性分层（43，含 JSON attrs） ----------------------------
-- ZK-2401：R1 0~86.80
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(1,1,1,'第四系覆盖层',  0.00, 12.50,'黄褐色','全风化','松散结构','残坡积层，含砂砾，顶部见腐殖土',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","矿化":"无","取样编号":"TR-2401-01","岩芯完整度":"散体"}'),
(1,1,2,'砂岩',        12.50, 86.80,'灰白色','微风化','中粒结构','中厚层石英砂岩，钙质胶结，岩芯完整呈长柱状',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"不发育","矿化":"无","蚀变":"无","取样编号":"HQ-2401-02","岩芯完整度":"完整"}');
-- ZK-2401：R2 86.80~173.60
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(2,1,1,'泥岩',        86.80,131.00,'深灰色','弱风化','层状构造','泥质结构，页理发育，局部见炭质纹层',
 '{"含水性":"隔水","硬度":"较软","裂隙发育":"弱发育","蚀变":"轻微高岭土化","取样编号":"HQ-2401-03","岩芯完整度":"较完整"}'),
(2,1,2,'粉砂岩',     131.00,173.60,'灰绿色','弱风化','薄层状构造','粉砂质结构，夹泥岩条带，层面见白云母片',
 '{"含水性":"隔水","硬度":"中等","裂隙发育":"弱发育","蚀变":"绿泥石化","取样编号":"HQ-2401-04","岩芯完整度":"较完整"}');
-- ZK-2401：R3 173.60~260.40
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(3,1,1,'安山岩',     173.60,260.40,'紫红色','中等风化','斑状结构','斑晶为斜长石，基质隐晶质，见杏仁状构造',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"中等发育","矿化":"星点状黄铁矿","蚀变":"绿泥石化","取样编号":"HQ-2401-05"}');
-- ZK-2401：R4 260.40~347.20
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(4,1,1,'破碎带',     260.40,347.20,'灰黑色','强风化','角砾状构造','构造破碎带，角砾成分为安山岩，断层泥发育，钻进漏失',
 '{"含水性":"严重漏失","硬度":"破碎","裂隙发育":"极发育","矿化":"无","蚀变":"断层泥化","岩芯完整度":"破碎","备注":"冲洗液全漏失，顶漏钻进"}');
-- ZK-2401：R5 347.20~434.00
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(5,1,1,'蚀变带',     347.20,390.50,'灰绿色','强风化','块状构造','绿泥石化、绢云母化蚀变强烈，见黄铁矿浸染',
 '{"含水性":"中等涌水","硬度":"较软","裂隙发育":"中等发育","矿化":"黄铁矿浸染","蚀变":"绿泥石化+绢云母化","取样编号":"HQ-2401-06"}'),
(5,1,2,'石英脉',     390.50,434.00,'乳白色','未风化','块状构造','石英脉穿插，脉宽 0.3~0.8 m，见星点状黄铜矿',
 '{"含水性":"干燥","硬度":"极坚硬","裂隙发育":"不发育","矿化":"黄铜矿星点状","蚀变":"硅化","取样编号":"HQ-2401-07"}');
-- ZK-2401：R6 434.00~520.50
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(6,1,1,'矿化带',     434.00,520.50,'铅灰色','中等风化','细粒结构','铅锌矿化带，方铅矿闪锌矿呈细脉浸染状，岩芯破碎',
 '{"含水性":"中等涌水","硬度":"中等","裂隙发育":"发育","矿化":"方铅矿-闪锌矿","蚀变":"碳酸盐化","取样编号":"HQ-2401-08","岩芯完整度":"破碎"}');
-- ZK-2402：R7 0~121.50
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(7,2,1,'第四系覆盖层',  0.00, 18.60,'棕黄色','全风化','松散结构','风积砂土夹砾石，孔壁易塌',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","矿化":"无","岩芯完整度":"散体"}'),
(7,2,2,'大理岩',       18.60,121.50,'灰白色','微风化','中粒结构','厚层大理岩，方解石晶粒粗大，岩芯长柱状',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"不发育","矿化":"无","蚀变":"无","取样编号":"HQ-2402-01"}');
-- ZK-2402：R8 121.50~243.00
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(8,2,1,'大理岩',     121.50,243.00,'青灰色','弱风化','条带状构造','条带状大理岩，夹薄层透闪石，层面平整',
 '{"含水性":"隔水","硬度":"坚硬","裂隙发育":"弱发育","蚀变":"透闪石化","取样编号":"HQ-2402-02"}');
-- ZK-2402：R9 243.00~364.50
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(9,2,1,'片麻岩',     243.00,364.50,'深灰色','中等风化','片麻状构造','黑云斜长片麻岩，片麻理倾角约 35°',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"中等发育","蚀变":"弱绿泥石化","取样编号":"HQ-2402-03"}');
-- ZK-2402：R10 364.50~486.00
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(10,2,1,'片麻岩',    364.50,486.00,'肉红色','强风化','片麻状构造','风化片麻岩，长石高岭土化，岩芯短柱状',
 '{"含水性":"隔水","硬度":"较软","裂隙发育":"发育","蚀变":"高岭土化","取样编号":"HQ-2402-04","岩芯完整度":"破碎"}');
-- ZK-2403：R11 0~104.30
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(11,3,1,'砾岩',        0.00,104.30,'灰褐色','强风化','角砾状构造','钙质胶结砾岩，砾径 2~8 cm，胶结疏松',
 '{"含水性":"中等涌水","硬度":"较硬","裂隙发育":"发育","矿化":"无","岩芯完整度":"块状"}');
-- ZK-2403：R12 104.30~208.60
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(12,3,1,'砂岩',      104.30,208.60,'灰绿色','弱风化','中粒结构','长石石英砂岩，泥质胶结，需泥浆护壁',
 '{"含水性":"微弱渗水","硬度":"中等","裂隙发育":"弱发育","蚀变":"弱绿泥石化","取样编号":"HQ-2403-01"}');
-- ZK-2403：R13 208.60~312.80
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(13,3,1,'花岗岩',    208.60,312.80,'肉红色','微风化','斑状结构','中粗粒黑云母花岗岩，石英含量约 28%',
 '{"含水性":"干燥","硬度":"极坚硬","裂隙发育":"不发育","矿化":"无","蚀变":"无","取样编号":"HQ-2403-02"}');
-- ZK-2404：R14 0~68.50
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(14,4,1,'第四系覆盖层', 0.00, 68.50,'黄褐色','全风化','松散结构','冲洪积砂砾石层，含漂砾，跟管至 42 m',
 '{"含水性":"强涌水","硬度":"松散","裂隙发育":"不发育","矿化":"无","岩芯完整度":"散体","备注":"套管跟至 42.00 m"}');
-- ZK-3101：R15 0~216.10
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(15,5,1,'第四系覆盖层',  0.00, 34.20,'灰黄色','全风化','松散结构','戈壁砾石层，风成砂充填',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","岩芯完整度":"散体"}'),
(15,5,2,'灰岩',        34.20,216.10,'深灰色','微风化','块状构造','厚层生物碎屑灰岩，方解石脉发育',
 '{"含水性":"岩溶水","硬度":"坚硬","裂隙发育":"中等发育","矿化":"无","蚀变":"弱大理岩化","取样编号":"HQ-3101-01"}');
-- ZK-3101：R16 216.10~432.20
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(16,5,1,'灰岩',      216.10,318.50,'深灰色','未风化','块状构造','致密灰岩，偶见燧石结核，岩芯完整',
 '{"含水性":"隔水","硬度":"极坚硬","裂隙发育":"不发育","取样编号":"HQ-3101-02"}'),
(16,5,2,'白云岩',    318.50,432.20,'灰白色','弱风化','细粒结构','细晶白云岩，具砂糖状断口，见溶蚀孔洞',
 '{"含水性":"岩溶水","硬度":"坚硬","裂隙发育":"发育","蚀变":"白云岩化","取样编号":"HQ-3101-03","备注":"溶孔直径 1~5 cm"}');
-- ZK-3101：R17 432.20~648.30
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(17,5,1,'灰岩',      432.20,648.30,'灰黑色','弱风化','块状构造','含炭质灰岩，岩溶裂隙发育，多次漏失堵漏',
 '{"含水性":"严重漏失","硬度":"坚硬","裂隙发育":"极发育","蚀变":"弱硅化","岩芯完整度":"破碎","备注":"累计漏失 4 次，水泥堵漏"}');
-- ZK-3101：R18 648.30~864.20
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(18,5,1,'花岗岩',    648.30,864.20,'肉红色','微风化','斑状结构','基底花岗岩，钾长石斑晶 5~12 mm',
 '{"含水性":"干燥","硬度":"极坚硬","裂隙发育":"不发育","矿化":"无","取样编号":"HQ-3101-04"}');
-- ZK-3102：R19 0~247.50
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(19,6,1,'第四系覆盖层',  0.00, 26.80,'灰黄色','全风化','松散结构','风积砂层，含少量细砾',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","岩芯完整度":"散体"}'),
(19,6,2,'砂岩',        26.80,247.50,'浅灰色','微风化','中粒结构','石英砂岩，硅质胶结，可钻性中等',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"弱发育","取样编号":"HQ-3102-01"}');
-- ZK-3102：R20 247.50~495.00
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(20,6,1,'泥岩',      247.50,495.00,'灰黑色','弱风化','层状构造','含炭泥岩，遇水膨胀缩径，需反复扫孔',
 '{"含水性":"隔水","硬度":"较软","裂隙发育":"弱发育","蚀变":"弱伊利石化","岩芯完整度":"较完整","备注":"缩径段 320~410 m"}');
-- ZK-3102：R21 495.00~742.60
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(21,6,1,'砂岩',      495.00,742.60,'灰绿色','微风化','细粒结构','细粒长石砂岩，含水性增强，见层间水',
 '{"含水性":"中等涌水","硬度":"中等","裂隙发育":"中等发育","取样编号":"HQ-3102-02"}');
-- ZK-3103：R22 0~176.10
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(22,7,1,'第四系覆盖层',  0.00, 21.40,'棕灰色','全风化','松散结构','坡积碎石土，含基岩风化碎块',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","岩芯完整度":"散体"}'),
(22,7,2,'安山岩',      21.40,176.10,'紫红色','弱风化','斑状结构','安山岩，斜长石斑晶，见气孔构造',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"弱发育","矿化":"无","蚀变":"弱绿帘石化","取样编号":"HQ-3103-01"}');
-- ZK-3103：R23 176.10~352.20
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(23,7,1,'安山岩',    176.10,352.20,'灰绿色','中等风化','杏仁状构造','杏仁体为方解石与绿泥石，充填率约 15%',
 '{"含水性":"隔水","硬度":"坚硬","裂隙发育":"中等发育","蚀变":"绿泥石化+绿帘石化","取样编号":"HQ-3103-02"}');
-- ZK-3103：R24 352.20~528.40
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(24,7,1,'矿化带',    352.20,528.40,'铅灰色','强风化','细脉浸染状构造','铜镍矿化细脉沿裂隙充填，脉宽 1~5 mm',
 '{"含水性":"微弱渗水","硬度":"较软","裂隙发育":"发育","矿化":"黄铜矿-镍黄铁矿","蚀变":"蛇纹石化","取样编号":"HQ-3103-03"}');
-- ZK-5201：R25 0~152.80
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(25,8,1,'砂岩',        0.00,152.80,'灰白色','微风化','中粒结构','河流相砂岩，交错层理发育',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"不发育","取样编号":"HQ-5201-01"}');
-- ZK-5201：R26 152.80~305.60
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(26,8,1,'页岩',      152.80,305.60,'灰黑色','弱风化','页理构造','炭质页岩，页理极发育，易水化掉块',
 '{"含水性":"隔水","硬度":"软","裂隙发育":"弱发育","蚀变":"弱炭化","岩芯完整度":"饼状","备注":"掉块 2 次"}');
-- ZK-5201：R27 305.60~458.30
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(27,8,1,'石英脉',    305.60,458.30,'乳白色','未风化','块状构造','石英脉密集带，脉体与围岩接触面见断层擦痕',
 '{"含水性":"干燥","硬度":"极坚硬","裂隙发育":"中等发育","矿化":"自然金-黄铁矿","蚀变":"硅化+绢云母化","取样编号":"HQ-5201-02"}');
-- ZK-5202：R28 0~98.40
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(28,9,1,'第四系覆盖层', 0.00, 98.40,'黄褐色','全风化','松散结构','高原草甸腐殖土与冰碛砾石，冻结层发育',
 '{"含水性":"季节冻土水","硬度":"松散","裂隙发育":"不发育","岩芯完整度":"散体","备注":"海拔 3215 m"}');
-- ZK-5202：R29 98.40~196.70
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(29,9,1,'花岗岩',     98.40,196.70,'浅肉红色','微风化','中粒结构','中粒二长花岗岩，节理较发育，钻头磨损快',
 '{"含水性":"干燥","硬度":"极坚硬","裂隙发育":"中等发育","蚀变":"弱钾长石化","取样编号":"HQ-5202-01"}');
-- ZK-6401：R30 0~134.50
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(30,10,1,'第四系覆盖层', 0.00, 15.60,'灰黄色','全风化','松散结构','草原风成砂，含少量钙质结核',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","岩芯完整度":"散体"}'),
(30,10,2,'片麻岩',     15.60,134.50,'深灰色','中等风化','片麻状构造','角闪斜长片麻岩，局部混合岩化',
 '{"含水性":"微弱渗水","硬度":"坚硬","裂隙发育":"中等发育","蚀变":"混合岩化","取样编号":"HQ-6401-01"}');
-- ZK-6401：R31 134.50~268.90
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(31,10,1,'矿化带',   134.50,268.90,'铅灰色','弱风化','细脉浸染状构造','铅锌矿化沿片麻理充填，伴生方铅矿团块',
 '{"含水性":"隔水","硬度":"中等","裂隙发育":"发育","矿化":"方铅矿-闪锌矿","蚀变":"绿泥石化","取样编号":"HQ-6401-02"}');
-- ZK-7201：R32 0~32.00
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(32,11,1,'第四系覆盖层', 0.00, 32.00,'黄褐色','全风化','松散结构','山坡残积土，含磁铁矿转石',
 '{"含水性":"干燥","硬度":"松散","裂隙发育":"不发育","矿化":"转石见磁铁矿","岩芯完整度":"散体"}');
-- ZK-7202：R33 0~76.20
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(33,12,1,'砂岩',       0.00, 76.20,'灰绿色','弱风化','细粒结构','细粒杂砂岩，含磁铁矿条纹',
 '{"含水性":"微弱渗水","硬度":"中等","裂隙发育":"弱发育","矿化":"磁铁矿条纹","取样编号":"HQ-7202-01"}');
-- ZK-7202：R34 76.20~152.40
INSERT INTO litho_layer (round_id, borehole_id, layer_seq, lithology, from_depth, to_depth, color, weathering, structure, description, attrs) VALUES
(34,12,1,'泥岩',      76.20,152.40,'深灰色','中等风化','层状构造','粉砂质泥岩，含铁质结核，封孔前终孔层位',
 '{"含水性":"隔水","硬度":"较软","裂隙发育":"弱发育","矿化":"磁铁矿细小颗粒","取样编号":"HQ-7202-02"}');

-- ---------------------------- 钻进参数时序（3 条 × 24 点，JSON points） ----------------------------
INSERT INTO drill_param_series (borehole_id, rig_id, record_date, shift_type, operator, points) VALUES
(3,2,'2024-08-11','早','周立军',
 '[
  {"time":"08:00","pressure":22.5,"rotation":210,"pump":120,"mudDensity":1.12,"holeNote":"孔内正常，返砂均匀"},
  {"time":"08:30","pressure":24.0,"rotation":215,"pump":125,"mudDensity":1.12,"holeNote":"孔内正常"},
  {"time":"09:00","pressure":25.6,"rotation":220,"pump":128,"mudDensity":1.13,"holeNote":"进尺 1.8 m，岩性砂岩"},
  {"time":"09:30","pressure":26.8,"rotation":218,"pump":130,"mudDensity":1.13,"holeNote":"孔内正常"},
  {"time":"10:00","pressure":52.4,"rotation":240,"pump":135,"mudDensity":1.15,"holeNote":"钻压异常升高，疑似孔底岩性变硬"},
  {"time":"10:30","pressure":38.2,"rotation":235,"pump":138,"mudDensity":1.15,"holeNote":"调整钻压后恢复正常"},
  {"time":"11:00","pressure":28.4,"rotation":228,"pump":140,"mudDensity":1.14,"holeNote":"孔内正常，返砂增多"},
  {"time":"11:30","pressure":29.1,"rotation":230,"pump":142,"mudDensity":1.14,"holeNote":"进尺 2.1 m"},
  {"time":"12:00","pressure":30.5,"rotation":232,"pump":145,"mudDensity":1.15,"holeNote":"孔内正常"},
  {"time":"12:30","pressure":31.2,"rotation":236,"pump":148,"mudDensity":1.16,"holeNote":"泥浆密度略升，补加膨润土"},
  {"time":"13:00","pressure":30.8,"rotation":238,"pump":150,"mudDensity":1.18,"holeNote":"孔内正常"},
  {"time":"13:30","pressure":29.6,"rotation":520,"pump":152,"mudDensity":1.18,"holeNote":"转速失控，离合器打滑后跳档"},
  {"time":"14:00","pressure":27.4,"rotation":245,"pump":150,"mudDensity":1.19,"holeNote":"降速处理，检查传动"},
  {"time":"14:30","pressure":28.1,"rotation":242,"pump":148,"mudDensity":1.19,"holeNote":"孔内正常"},
  {"time":"15:00","pressure":29.3,"rotation":240,"pump":146,"mudDensity":1.20,"holeNote":"进尺 1.6 m"},
  {"time":"15:30","pressure":30.7,"rotation":238,"pump":145,"mudDensity":1.20,"holeNote":"孔内正常"},
  {"time":"16:00","pressure":32.4,"rotation":236,"pump":143,"mudDensity":1.21,"holeNote":"孔口返浆正常"},
  {"time":"16:30","pressure":33.8,"rotation":234,"pump":141,"mudDensity":1.21,"holeNote":"孔内正常"},
  {"time":"17:00","pressure":34.5,"rotation":230,"pump":140,"mudDensity":1.22,"holeNote":"钻头磨损，准备提钻"},
  {"time":"17:30","pressure":20.1,"rotation":180,"pump":96,"mudDensity":1.22,"holeNote":"提钻换钻头，停止进尺"},
  {"time":"18:00","pressure":16.4,"rotation":150,"pump":48,"mudDensity":1.22,"holeNote":"泵量偏低，检修泥浆泵"},
  {"time":"18:30","pressure":22.8,"rotation":205,"pump":118,"mudDensity":1.21,"holeNote":"泵量恢复，扫孔到底"},
  {"time":"19:00","pressure":26.2,"rotation":215,"pump":124,"mudDensity":1.20,"holeNote":"孔内正常，恢复钻进"},
  {"time":"19:30","pressure":27.0,"rotation":218,"pump":126,"mudDensity":1.20,"holeNote":"交接班，孔内干净"}
 ]'),
(9,4,'2024-05-20','中','黄志刚',
 '[
  {"time":"08:00","pressure":18.5,"rotation":180,"pump":105,"mudDensity":1.09,"holeNote":"花岗岩段，进尺慢"},
  {"time":"08:30","pressure":19.2,"rotation":185,"pump":108,"mudDensity":1.09,"holeNote":"孔内正常"},
  {"time":"09:00","pressure":20.4,"rotation":190,"pump":110,"mudDensity":1.10,"holeNote":"进尺 0.6 m"},
  {"time":"09:30","pressure":21.0,"rotation":192,"pump":112,"mudDensity":1.10,"holeNote":"孔内正常"},
  {"time":"10:00","pressure":22.3,"rotation":195,"pump":115,"mudDensity":1.11,"holeNote":"钻头唇面磨损"},
  {"time":"10:30","pressure":23.1,"rotation":198,"pump":116,"mudDensity":1.11,"holeNote":"孔内正常"},
  {"time":"11:00","pressure":24.6,"rotation":200,"pump":118,"mudDensity":1.12,"holeNote":"进尺 0.7 m"},
  {"time":"11:30","pressure":25.2,"rotation":205,"pump":120,"mudDensity":1.12,"holeNote":"孔内正常"},
  {"time":"12:00","pressure":26.0,"rotation":208,"pump":122,"mudDensity":1.13,"holeNote":"孔口返浆正常"},
  {"time":"12:30","pressure":26.7,"rotation":210,"pump":124,"mudDensity":1.13,"holeNote":"孔内正常"},
  {"time":"13:00","pressure":27.4,"rotation":212,"pump":126,"mudDensity":1.14,"holeNote":"进尺 0.8 m"},
  {"time":"13:30","pressure":41.6,"rotation":215,"pump":128,"mudDensity":1.42,"holeNote":"泥浆密度异常，误加加重剂过量"},
  {"time":"14:00","pressure":32.0,"rotation":210,"pump":130,"mudDensity":1.34,"holeNote":"加清水稀释中"},
  {"time":"14:30","pressure":28.4,"rotation":206,"pump":128,"mudDensity":1.26,"holeNote":"密度回落"},
  {"time":"15:00","pressure":27.1,"rotation":204,"pump":126,"mudDensity":1.20,"holeNote":"孔内正常"},
  {"time":"15:30","pressure":26.8,"rotation":202,"pump":125,"mudDensity":1.18,"holeNote":"孔内正常"},
  {"time":"16:00","pressure":27.6,"rotation":204,"pump":124,"mudDensity":1.18,"holeNote":"进尺 0.5 m"},
  {"time":"16:30","pressure":28.2,"rotation":206,"pump":123,"mudDensity":1.17,"holeNote":"孔内正常"},
  {"time":"17:00","pressure":29.0,"rotation":208,"pump":122,"mudDensity":1.17,"holeNote":"岩粉增多，加强循环"},
  {"time":"17:30","pressure":30.1,"rotation":212,"pump":120,"mudDensity":1.16,"holeNote":"孔内正常"},
  {"time":"18:00","pressure":31.4,"rotation":214,"pump":119,"mudDensity":1.16,"holeNote":"进尺 0.6 m"},
  {"time":"18:30","pressure":32.2,"rotation":216,"pump":118,"mudDensity":1.15,"holeNote":"孔内正常"},
  {"time":"19:00","pressure":33.0,"rotation":218,"pump":117,"mudDensity":1.15,"holeNote":"孔壁稳定"},
  {"time":"19:30","pressure":31.8,"rotation":215,"pump":116,"mudDensity":1.15,"holeNote":"交接班，孔内干净"}
 ]'),
(4,5,'2024-08-06','夜','杨树森',
 '[
  {"time":"20:00","pressure":12.4,"rotation":140,"pump":150,"mudDensity":1.08,"holeNote":"开孔覆盖层，轻压慢转"},
  {"time":"20:30","pressure":13.1,"rotation":145,"pump":155,"mudDensity":1.08,"holeNote":"孔内正常"},
  {"time":"21:00","pressure":14.0,"rotation":150,"pump":160,"mudDensity":1.09,"holeNote":"进尺 2.4 m"},
  {"time":"21:30","pressure":14.6,"rotation":152,"pump":162,"mudDensity":1.09,"holeNote":"孔壁有坍塌迹象"},
  {"time":"22:00","pressure":15.2,"rotation":155,"pump":165,"mudDensity":1.10,"holeNote":"提高泥浆粘度护壁"},
  {"time":"22:30","pressure":16.0,"rotation":158,"pump":168,"mudDensity":1.12,"holeNote":"返砂含砾石"},
  {"time":"23:00","pressure":16.8,"rotation":160,"pump":170,"mudDensity":1.13,"holeNote":"进尺 2.6 m"},
  {"time":"23:30","pressure":17.4,"rotation":162,"pump":172,"mudDensity":1.14,"holeNote":"孔内正常"},
  {"time":"00:00","pressure":18.2,"rotation":165,"pump":175,"mudDensity":1.15,"holeNote":"跟管钻进至 30 m"},
  {"time":"00:30","pressure":19.0,"rotation":168,"pump":178,"mudDensity":1.16,"holeNote":"孔内正常"},
  {"time":"01:00","pressure":19.8,"rotation":170,"pump":180,"mudDensity":1.17,"holeNote":"进尺 2.2 m"},
  {"time":"01:30","pressure":20.6,"rotation":172,"pump":182,"mudDensity":1.18,"holeNote":"孔内正常"},
  {"time":"02:00","pressure":21.4,"rotation":175,"pump":185,"mudDensity":1.19,"holeNote":"砾径变大，钻具跳动"},
  {"time":"02:30","pressure":23.8,"rotation":180,"pump":190,"mudDensity":1.20,"holeNote":"钻具跳动加剧，减压钻进"},
  {"time":"03:00","pressure":22.1,"rotation":176,"pump":186,"mudDensity":1.20,"holeNote":"孔内正常"},
  {"time":"03:30","pressure":21.6,"rotation":174,"pump":184,"mudDensity":1.19,"holeNote":"进尺 1.9 m"},
  {"time":"04:00","pressure":20.8,"rotation":172,"pump":180,"mudDensity":1.18,"holeNote":"孔内正常"},
  {"time":"04:30","pressure":20.2,"rotation":170,"pump":178,"mudDensity":1.17,"holeNote":"水泥浆护壁一次"},
  {"time":"05:00","pressure":19.6,"rotation":168,"pump":176,"mudDensity":1.16,"holeNote":"进尺 1.7 m"},
  {"time":"05:30","pressure":19.0,"rotation":165,"pump":172,"mudDensity":1.15,"holeNote":"孔内正常"},
  {"time":"06:00","pressure":18.4,"rotation":162,"pump":170,"mudDensity":1.14,"holeNote":"接近基岩面"},
  {"time":"06:30","pressure":24.6,"rotation":168,"pump":168,"mudDensity":1.14,"holeNote":"钻遇基岩，钻压突增"},
  {"time":"07:00","pressure":26.2,"rotation":172,"pump":166,"mudDensity":1.13,"holeNote":"进入砂岩，进尺放慢"},
  {"time":"07:30","pressure":25.8,"rotation":170,"pump":164,"mudDensity":1.13,"holeNote":"交接班，套管下至 42 m"}
 ]');

-- ---------------------------- 班报表（8，含 JSON materials） ----------------------------
INSERT INTO shift_report (borehole_id, rig_id, crew_id, report_date, shift_type, footage, pure_hours, aux_hours, accident_note, materials, handover_note, leader_sign) VALUES
(1,1,1,'2024-05-25','早', 18.60, 6.20, 1.40,'无事故，孔内正常',
 '[{"category":"钻头","name":"金刚石钻头","spec":"Φ75 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"膨润土","spec":"25 kg/袋","qty":4,"unit":"袋"},{"category":"泥浆材料","name":"CMC","spec":"高粘","qty":2,"unit":"kg"}]',
 '孔内干净，钻具完好，下一班继续 6 回次钻进','刘铁柱'),
(1,1,2,'2024-05-25','中', 15.20, 5.60, 2.10,'14:20 泥浆泵上水不畅，停待 25 min 检修',
 '[{"category":"钻头","name":"金刚石扩孔器","spec":"Φ75.5 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"重晶石粉","spec":"25 kg/袋","qty":3,"unit":"袋"},{"category":"泥浆材料","name":"烧碱","spec":"工业级","qty":1,"unit":"kg"}]',
 '破碎带漏失，已备足堵漏材料，注意观察返浆','马三保'),
(3,2,3,'2024-08-10','夜', 21.40, 6.80, 1.00,'无事故',
 '[{"category":"钻头","name":"金刚石钻头","spec":"Φ75 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"膨润土","spec":"25 kg/袋","qty":5,"unit":"袋"},{"category":"泥浆材料","name":"润滑剂","spec":"RH-3","qty":2,"unit":"kg"}]',
 '孔内返砂均匀，进尺正常，注意钻头磨损','周立军'),
(4,5,5,'2024-08-06','夜', 24.80, 5.40, 2.30,'01:40 孔壁坍塌，水泥浆护壁 1 次，停待 40 min',
 '[{"category":"钻头","name":"牙轮钻头","spec":"Φ130 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"水泥","spec":"P.O 42.5","qty":6,"unit":"袋"},{"category":"泥浆材料","name":"膨润土","spec":"25 kg/袋","qty":8,"unit":"袋"}]',
 '套管已下至 42 m，下一班可换径钻进','杨树森'),
(6,3,4,'2023-10-29','早', 26.50, 6.50, 1.30,'无事故，泥岩段缩径需扫孔',
 '[{"category":"钻头","name":"金刚石钻头","spec":"Φ95 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"聚丙烯酰胺","spec":"PAM","qty":3,"unit":"kg"},{"category":"泥浆材料","name":"膨润土","spec":"25 kg/袋","qty":6,"unit":"袋"}]',
 '320~410 m 缩径，已扫孔到底，保持泥浆性能','陈大勇'),
(9,4,7,'2024-05-19','中', 9.70, 6.00, 1.80,'无事故，花岗岩段可钻性差，进尺慢',
 '[{"category":"钻头","name":"金刚石钻头","spec":"Φ75 mm","qty":2,"unit":"个"},{"category":"泥浆材料","name":"膨润土","spec":"25 kg/袋","qty":2,"unit":"袋"}]',
 '钻头磨损快，库房钻头存量不足，已报计划','黄志刚'),
(11,5,5,'2024-09-04','早', 32.00, 4.20, 2.60,'开孔安装，平整场地耗时 1.5 h',
 '[{"category":"钻头","name":"硬质合金钻头","spec":"Φ110 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"膨润土","spec":"25 kg/袋","qty":5,"unit":"袋"},{"category":"泥浆材料","name":"套管靴","spec":"Φ127 mm","qty":2,"unit":"个"}]',
 '开孔完成，孔口管已固定，下一班跟管钻进','杨树森'),
(12,6,8,'2024-07-11','中', 22.30, 5.80, 1.60,'无事故，封孔前最后一个班',
 '[{"category":"钻头","name":"金刚石钻头","spec":"Φ75 mm","qty":1,"unit":"个"},{"category":"泥浆材料","name":"水泥","spec":"P.O 42.5","qty":10,"unit":"袋"}]',
 '已达设计孔深，准备封孔，封孔材料已到位','刘铁柱');

-- ---------------------------- 岩芯样品（12，含 2 条草稿） ----------------------------
INSERT INTO core_sample (id, borehole_id, sample_no, from_depth, to_depth, sampler, seal_no, sample_type, status, remark, batch_id, registered_at, created_at, updated_at, lock_version) VALUES
-- ZK-2401：矿化带 / 蚀变带 / 石英脉 化学样（批次1，已接收）
(1, 1,'HQ-2401-H01',442.00,445.00,'马三保','FS-2401-01','矿化样','RECEIVED','方铅矿细脉段，劈芯取样半芯',1,'2024-05-27 10:20:00','2024-05-27 10:20:00','2024-06-03 15:06:00',2),
(2, 1,'HQ-2401-H02',480.00,483.00,'马三保','FS-2401-02','矿化样','RECEIVED','矿化最强部位，加密取样',1,'2024-05-28 09:10:00','2024-05-28 09:10:00','2024-06-03 15:06:00',2),
(3, 1,'HQ-2401-H03',360.00,363.00,'刘铁柱','FS-2401-03','岩芯','RECEIVED','蚀变带黄铁矿浸染样',1,'2024-05-29 14:40:00','2024-05-29 14:40:00','2024-06-03 15:06:00',2),
-- ZK-2403：施工中，花岗岩/砂岩样（批次2，运输中）
(4, 3,'HQ-2403-H01',220.00,223.00,'周立军','FS-2403-01','岩芯','IN_TRANSIT','中粒花岗岩岩组鉴定样',2,'2024-08-13 08:30:00','2024-08-13 08:30:00','2024-08-20 07:50:00',1),
(5, 3,'HQ-2403-H02',150.00,153.00,'周立军','FS-2403-02','岩屑','IN_TRANSIT','砂岩薄片鉴定样',2,'2024-08-16 16:00:00','2024-08-16 16:00:00','2024-08-20 07:50:00',1),
-- ZK-3103：铜镍矿化（批次3，部分接收：1 接收 / 1 封签破损待补）
(6, 7,'HQ-3103-H01',360.00,363.00,'周立军','FS-3103-01','矿化样','RECEIVED','黄铜矿-镍黄铁矿细脉',3,'2024-05-06 11:00:00','2024-05-06 11:00:00','2024-05-12 10:20:00',2),
(7, 7,'HQ-3103-H02',450.00,453.00,'周立军','FS-3103-02','矿化样','ABNORMAL','到达实验室时封签破损，箱内样品可辨，待补封签',3,'2024-05-07 09:30:00','2024-05-07 09:30:00','2024-05-12 10:20:00',2),
-- ZK-2402：曾整批退回，样品回到待送检（批次4，已退回）
(8, 2,'HQ-2402-H01',260.00,263.00,'马三保','FS-2402-01','岩芯','REGISTERED','片麻岩岩组样',NULL,'2024-06-20 10:00:00','2024-06-20 10:00:00','2024-06-28 17:30:00',3),
-- 待送检散样
(9, 5,'HQ-3101-H01',660.00,663.00,'陈大勇','FS-3101-01','岩芯','REGISTERED','花岗岩基底定名样',NULL,'2023-10-20 11:00:00','2023-10-20 11:00:00','2023-10-20 11:00:00',0),
(10,9,'HQ-5202-H01',120.00,123.00,'黄志刚','FS-5202-01','岩芯','REGISTERED','二长花岗岩同位素样',NULL,'2024-05-22 10:00:00','2024-05-22 10:00:00','2024-05-22 10:00:00',0),
-- 草稿（关键信息不全：1 缺样品编号与封签；1 缺取样人）
(11,3,NULL,280.00,283.00,NULL,NULL,'矿化样','DRAFT','现场初步描述：见星点状黄铁矿，待补编号封签',NULL,NULL,'2024-08-18 15:00:00','2024-08-18 15:00:00',0),
(12,4,'HQ-2404-S01',10.00,12.00,NULL,'FS-2404-01','水样','DRAFT','覆盖层潜水样，取样人未登记',NULL,NULL,'2024-08-08 09:00:00','2024-08-08 09:00:00',0);

-- ---------------------------- 送检批次（4：已接收 / 运输中 / 部分接收 / 已退回） ----------------------------
INSERT INTO sample_batch (id, batch_no, status, expected_count, received_count, abnormal_count, packer, carrier, pack_note, packed_at, receiver, received_at, receive_note, return_reason, returned_at, lock_version, created_at) VALUES
(1,'SJ-20240601-01','RECEIVED',3,3,0,'马三保','刘成','木箱铅封，附送样单 1 份','2024-06-01 18:00:00','实验室-吴敏','2024-06-03 15:06:00','数量、封签核对一致，样品完好',NULL,NULL,2,'2024-06-01 18:00:00'),
(2,'SJ-20240820-01','IN_TRANSIT',2,0,0,'周立军','郑凯','泡沫箱分装，汽运至中心实验室','2024-08-20 07:50:00',NULL,NULL,NULL,NULL,NULL,1,'2024-08-20 07:50:00'),
(3,'SJ-20240510-01','PARTIAL_RECEIVED',2,1,1,'周立军','郑凯','2 件矿化样分别袋装','2024-05-10 08:00:00','实验室-吴敏','2024-05-12 10:20:00','1 件封签破损，已原样暂存待补封签，其余 1 件正常接收',NULL,NULL,2,'2024-05-10 08:00:00'),
(4,'SJ-20240625-01','RETURNED',1,0,0,'马三保','外协司机','片麻岩样 1 件','2024-06-25 09:00:00',NULL,NULL,NULL,'送样单缺失且外箱无批次标签，无法核对来源，整批退回补齐手续','2024-06-28 17:30:00',2,'2024-06-25 09:00:00');

-- ---------------------------- 批次样品明细（8 行） ----------------------------
INSERT INTO sample_batch_item (id, batch_id, sample_id, sample_no, borehole_id, from_depth, to_depth, seal_no, item_status, abnormal_note, received_at, receiver) VALUES
(1,1,1,'HQ-2401-H01',1,442.00,445.00,'FS-2401-01','RECEIVED',NULL,'2024-06-03 15:06:00','实验室-吴敏'),
(2,1,2,'HQ-2401-H02',1,480.00,483.00,'FS-2401-02','RECEIVED',NULL,'2024-06-03 15:06:00','实验室-吴敏'),
(3,1,3,'HQ-2401-H03',1,360.00,363.00,'FS-2401-03','RECEIVED',NULL,'2024-06-03 15:06:00','实验室-吴敏'),
(4,2,4,'HQ-2403-H01',3,220.00,223.00,'FS-2403-01','PENDING',NULL,NULL,NULL),
(5,2,5,'HQ-2403-H02',3,150.00,153.00,'FS-2403-02','PENDING',NULL,NULL,NULL),
(6,3,6,'HQ-3103-H01',7,360.00,363.00,'FS-3103-01','RECEIVED',NULL,'2024-05-12 10:20:00','实验室-吴敏'),
(7,3,7,'HQ-3103-H02',7,450.00,453.00,'FS-3103-02','ABNORMAL','到达时封签 FS-3103-02 破损，样品可辨，待现场补封签与情况说明',NULL,NULL),
(8,4,8,'HQ-2402-H01',2,260.00,263.00,'FS-2402-01','RETURNED','送样单缺失、外箱无批次标签，随批退回',NULL,NULL);

-- ---------------------------- 批次交接事件（6 条，只增不改） ----------------------------
INSERT INTO sample_batch_event (id, batch_id, event_type, event_status, sample_count, received_count, abnormal_count, operator, note, event_at) VALUES
(1,1,'PACK','IN_TRANSIT',3,0,0,'马三保','封样发运：3 件，木箱铅封','2024-06-01 18:00:00'),
(2,1,'RECEIVE','RECEIVED',3,3,0,'实验室-吴敏','数量、封签核对一致，全部接收','2024-06-03 15:06:00'),
(3,2,'PACK','IN_TRANSIT',2,0,0,'周立军','封样发运：2 件，汽运中心实验室','2024-08-20 07:50:00'),
(4,3,'PACK','IN_TRANSIT',2,0,0,'周立军','封样发运：2 件矿化样','2024-05-10 08:00:00'),
(5,3,'PARTIAL_RECEIVE','PARTIAL_RECEIVED',2,1,1,'实验室-吴敏','HQ-3103-H01 正常接收；HQ-3103-H02 封签破损，标待补','2024-05-12 10:20:00'),
(6,4,'PACK','IN_TRANSIT',1,0,0,'马三保','封样发运：1 件','2024-06-25 09:00:00'),
(7,4,'RETURN','RETURNED',1,0,0,'实验室-吴敏','送样单缺失且外箱无批次标签，整批退回','2024-06-28 17:30:00');
