USE pay_demo;
-- 演示种子数据（幂等）
INSERT IGNORE INTO t_user (user_no, user_name, balance) VALUES
('U1001', '张三', 0.00),
('U1002', '李四', 0.00),
('U1003', '王五', 0.00);
