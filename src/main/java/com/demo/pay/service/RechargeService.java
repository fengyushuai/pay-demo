package com.demo.pay.service;

import com.demo.pay.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 充值模块：
 * 1. 用户发起充值 -> 生成充值流水（PROCESSING），模拟请求充值渠道
 * 2. 渠道受理 -> 异步回调（演示中用接口模拟渠道回调）-> 余额实时入账
 * 3. 充值流水生成与查询
 * 4. 充值结果同步回传业务前台（FORWARD 状态记录回传动作）
 */
@Service
public class RechargeService {

    private final JdbcTemplate jdbc;
    private final NoGenerator noGen;

    public RechargeService(JdbcTemplate jdbc, NoGenerator noGen) {
        this.jdbc = jdbc;
        this.noGen = noGen;
    }

    /** 用户发起充值：创建 PROCESSING 流水并"下发渠道" */
    @Transactional
    public Map<String, Object> initiate(Long userId, double amount, String channel) {
        if (amount <= 0) {
            throw new IllegalArgumentException("充值金额必须大于0");
        }
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id FROM t_user WHERE id=?", userId);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        String rechargeNo = noGen.next("RC");
        String requestNo = "CH-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
            INSERT INTO t_recharge_flow(recharge_no, user_id, amount, channel, status, notify_content)
            VALUES (?,?,?,?, 'PROCESSING', ?)
            """, rechargeNo, userId, amount, channel, "已请求渠道[" + channel + "], 渠道受理号=" + requestNo);
        return ApiResponse.map("rechargeNo", rechargeNo, "status", "PROCESSING",
                "message", "充值请求已下发渠道" + channel + "，等待渠道回调入账");
    }

    /**
     * 模拟充值渠道异步回调（演示环境用接口代替真实第三方）。
     * result=success 时：余额实时入账 + 流水置 SUCCESS + 回传业务前台。
     */
    @Transactional
    public Map<String, Object> channelNotify(String rechargeNo, String result, String reason) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM t_recharge_flow WHERE recharge_no=? FOR UPDATE", rechargeNo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("充值单不存在: " + rechargeNo);
        }
        Map<String, Object> flow = rows.get(0);
        String status = (String) flow.get("status");
        if (!"PROCESSING".equals(status)) {
            throw new IllegalStateException("充值单状态不允许回调, 当前: " + status);
        }
        LocalDateTime now = noGen.now();
        if ("success".equalsIgnoreCase(result)) {
            double amount = ((Number) flow.get("amount")).doubleValue();
            Long userId = ((Number) flow.get("user_id")).longValue();
            // 余额实时入账
            jdbc.update("UPDATE t_user SET balance = balance + ? WHERE id=?", amount, userId);
            jdbc.update("""
                UPDATE t_recharge_flow SET status='SUCCESS', notify_time=?, notify_content=?
                WHERE recharge_no=?
                """, now, "渠道回调成功, 渠道流水=" + UUID.randomUUID(), rechargeNo);
            // 充值结果同步回传业务前台（演示：记录回传动作）
            jdbc.update("""
                UPDATE t_recharge_flow SET forward_status='DONE', forward_time=? WHERE recharge_no=?
                """, now, rechargeNo);
            return ApiResponse.map("rechargeNo", rechargeNo, "status", "SUCCESS",
                    "amount", amount, "forward", "已同步回传业务前台");
        } else {
            jdbc.update("""
                UPDATE t_recharge_flow SET status='FAIL', notify_time=?, fail_reason=?, notify_content=?
                WHERE recharge_no=?
                """, now, reason == null ? "渠道回调失败" : reason,
                    "渠道回调失败: " + reason, rechargeNo);
            return ApiResponse.map("rechargeNo", rechargeNo, "status", "FAIL");
        }
    }

    /** 充值流水查询 */
    public List<Map<String, Object>> list(Long userId, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT recharge_no, user_id, amount, channel, status, fail_reason, " +
                "forward_status, create_time, notify_time, forward_time " +
                "FROM t_recharge_flow WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (userId != null) { sql.append("AND user_id=? "); args.add(userId); }
        if (status != null && !status.isBlank()) { sql.append("AND status=? "); args.add(status); }
        sql.append("ORDER BY id DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> detail(String rechargeNo) {
        var rows = jdbc.queryForList("SELECT * FROM t_recharge_flow WHERE recharge_no=?", rechargeNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询用户余额 */
    public Map<String, Object> balance(Long userId) {
        return jdbc.queryForMap("SELECT user_no, user_name, balance FROM t_user WHERE id=?", userId);
    }
}
