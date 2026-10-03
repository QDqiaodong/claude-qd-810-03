-- H2（MySQL 兼容模式）专用的最小结构，仅覆盖样品送检链路测试涉及的表。
-- 与正式 MySQL schema.sql 中的字段/约束保持一致；去掉 MySQL 专有 ON UPDATE 子句。
CREATE TABLE borehole (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  mine_id      BIGINT NOT NULL,
  rig_id       BIGINT,
  hole_no      VARCHAR(32) NOT NULL UNIQUE,
  coord_x      DECIMAL(12,2) NOT NULL,
  coord_y      DECIMAL(14,2) NOT NULL,
  design_depth DECIMAL(8,2) NOT NULL,
  actual_depth DECIMAL(8,2) NOT NULL DEFAULT 0,
  spud_date    DATE,
  final_date   DATE,
  collar_elev  DECIMAL(8,2),
  dip_angle    DECIMAL(5,2),
  status       VARCHAR(16) NOT NULL
);

CREATE TABLE core_sample (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  borehole_id   BIGINT NOT NULL,
  sample_no     VARCHAR(40),
  from_depth    DECIMAL(8,2),
  to_depth      DECIMAL(8,2),
  sampler       VARCHAR(32),
  seal_no       VARCHAR(40),
  sample_type   VARCHAR(24),
  status        VARCHAR(12) NOT NULL,
  remark        VARCHAR(255),
  batch_id      BIGINT,
  registered_at DATETIME,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME,
  lock_version  INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_sample_no UNIQUE (sample_no),
  CONSTRAINT uk_sample_seal UNIQUE (seal_no)
);
CREATE INDEX idx_sample_hole ON core_sample (borehole_id);

CREATE TABLE sample_batch (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no        VARCHAR(40) NOT NULL UNIQUE,
  status          VARCHAR(18) NOT NULL,
  expected_count  INT NOT NULL,
  received_count  INT NOT NULL DEFAULT 0,
  abnormal_count  INT NOT NULL DEFAULT 0,
  packer          VARCHAR(32),
  carrier         VARCHAR(32),
  pack_note       VARCHAR(255),
  packed_at       DATETIME,
  receiver        VARCHAR(32),
  received_at     DATETIME,
  receive_note    VARCHAR(255),
  return_reason   VARCHAR(255),
  returned_at     DATETIME,
  lock_version    INT NOT NULL DEFAULT 0,
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sample_batch_item (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_id      BIGINT NOT NULL,
  sample_id     BIGINT NOT NULL,
  sample_no     VARCHAR(40) NOT NULL,
  borehole_id   BIGINT NOT NULL,
  from_depth    DECIMAL(8,2) NOT NULL,
  to_depth      DECIMAL(8,2) NOT NULL,
  seal_no       VARCHAR(40) NOT NULL,
  item_status   VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  abnormal_note VARCHAR(255),
  received_at   DATETIME,
  receiver      VARCHAR(32),
  CONSTRAINT uk_item_batch_sample UNIQUE (batch_id, sample_id)
);
CREATE INDEX idx_item_sample ON sample_batch_item (sample_id);
CREATE INDEX idx_item_batch ON sample_batch_item (batch_id);

CREATE TABLE sample_batch_event (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_id       BIGINT NOT NULL,
  event_type     VARCHAR(18) NOT NULL,
  event_status   VARCHAR(18) NOT NULL,
  sample_count   INT NOT NULL DEFAULT 0,
  received_count INT NOT NULL DEFAULT 0,
  abnormal_count INT NOT NULL DEFAULT 0,
  operator       VARCHAR(32),
  note           VARCHAR(255),
  event_at       DATETIME NOT NULL
);
CREATE INDEX idx_event_batch ON sample_batch_event (batch_id);
