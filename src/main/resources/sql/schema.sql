-- 建表脚本（幂等，可重复执行）
CREATE DATABASE IF NOT EXISTS pay_demo DEFAULT CHARSET utf8mb4;
USE pay_demo;

-- 用户账户表
CREATE TABLE IF NOT EXISTS t_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_no     VARCHAR(32)  NOT NULL UNIQUE COMMENT '用户编号',
  user_name   VARCHAR(64)  NOT NULL COMMENT '用户名',
  balance     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '账户余额',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT '用户账户';

-- 充值流水表
CREATE TABLE IF NOT EXISTS t_recharge_flow (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  recharge_no   VARCHAR(40) NOT NULL UNIQUE COMMENT '充值单号',
  user_id       BIGINT      NOT NULL,
  amount        DECIMAL(12,2) NOT NULL COMMENT '充值金额',
  channel       VARCHAR(20) NOT NULL COMMENT '充值渠道: WECHAT/ALIPAY/UNIONPAY',
  status        VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCESS/FAIL',
  fail_reason   VARCHAR(200) NULL,
  notify_content VARCHAR(500) NULL COMMENT '渠道回调报文',
  forward_status VARCHAR(20) NOT NULL DEFAULT 'WAIT' COMMENT '回传业务前台: WAIT/DONE',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notify_time   DATETIME NULL,
  forward_time  DATETIME NULL,
  INDEX idx_rf_user(user_id), INDEX idx_rf_status(status), INDEX idx_rf_time(create_time)
) ENGINE=InnoDB COMMENT '充值流水';

-- 支付流水表
CREATE TABLE IF NOT EXISTS t_pay_flow (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  pay_no        VARCHAR(40) NOT NULL UNIQUE COMMENT '支付单号',
  order_no      VARCHAR(40) NOT NULL COMMENT '关联业务订单号',
  user_id       BIGINT      NOT NULL,
  amount        DECIMAL(12,2) NOT NULL,
  channel       VARCHAR(20) NOT NULL COMMENT '支付通道: WECHAT/ALIPAY/UNIONPAY',
  route_type    VARCHAR(10) NOT NULL DEFAULT 'MANUAL' COMMENT '路由方式: AUTO/MANUAL',
  status        VARCHAR(20) NOT NULL DEFAULT 'PAYING' COMMENT 'PAYING/SUCCESS/FAIL',
  fail_reason   VARCHAR(200) NULL,
  notify_content VARCHAR(500) NULL COMMENT '异步回调报文',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notify_time   DATETIME NULL,
  INDEX idx_pf_order(order_no), INDEX idx_pf_status(status), INDEX idx_pf_time(create_time)
) ENGINE=InnoDB COMMENT '支付流水';

-- 业务订单表
CREATE TABLE IF NOT EXISTS t_biz_order (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no    VARCHAR(40) NOT NULL UNIQUE COMMENT '业务订单号',
  user_id     BIGINT      NOT NULL,
  title       VARCHAR(128) NOT NULL COMMENT '订单标题',
  amount      DECIMAL(12,2) NOT NULL COMMENT '订单金额',
  status      VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/CANCELED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  pay_time    DATETIME NULL,
  cancel_time DATETIME NULL,
  INDEX idx_bo_user(user_id), INDEX idx_bo_status(status)
) ENGINE=InnoDB COMMENT '业务订单';

-- 订单状态流转日志表
CREATE TABLE IF NOT EXISTS t_order_status_log (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no    VARCHAR(40) NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status   VARCHAR(20) NOT NULL,
  remark      VARCHAR(200) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_osl_order(order_no)
) ENGINE=InnoDB COMMENT '订单状态流转日志';

-- 稽核任务表
CREATE TABLE IF NOT EXISTS t_audit_task (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no     VARCHAR(40) NOT NULL UNIQUE,
  status      VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE',
  diff_count  INT NOT NULL DEFAULT 0,
  summary     VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finish_time DATETIME NULL
) ENGINE=InnoDB COMMENT '稽核任务';

-- 稽核差异明细表
CREATE TABLE IF NOT EXISTS t_audit_diff (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id    BIGINT NOT NULL,
  diff_type  VARCHAR(50) NOT NULL COMMENT '差异类型',
  biz_type   VARCHAR(20) NOT NULL COMMENT 'ORDER/PAY/RECHARGE',
  biz_no     VARCHAR(40) NOT NULL,
  detail     VARCHAR(500) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ad_task(task_id)
) ENGINE=InnoDB COMMENT '稽核差异明细';

-- 稽核日志表
CREATE TABLE IF NOT EXISTS t_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  log_level VARCHAR(10) NOT NULL DEFAULT 'INFO',
  content VARCHAR(500) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_al_task(task_id)
) ENGINE=InnoDB COMMENT '稽核日志';
