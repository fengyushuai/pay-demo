package com.demo.pay.service;

import com.demo.pay.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单模块：
 * 1. 创建业务订单
 * 2. 全生命周期状态：CREATED(待支付) / PAID(已支付) / CANCELED(已取消)
 * 3. 订单查询、关联充值/支付流水、状态变更追溯
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbc;
    private final NoGenerator noGen;

    public OrderService(JdbcTemplate jdbc, NoGenerator noGen) {
        this.jdbc = jdbc;
        this.noGen = noGen;
    }

    @Transactional
    public Map<String, Object> create(Long userId, String title, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("订单金额必须大于0");
        var users = jdbc.queryForList("SELECT id FROM t_user WHERE id=?", userId);
        if (users.isEmpty()) throw new IllegalArgumentException("用户不存在: " + userId);
        String orderNo = noGen.next("SO");
        jdbc.update("""
            INSERT INTO t_biz_order(order_no, user_id, title, amount, status) VALUES (?,?,?,?, 'CREATED')
            """, orderNo, userId, title, amount);
        jdbc.update("""
            INSERT INTO t_order_status_log(order_no, from_status, to_status, remark)
            VALUES (NULL, ?, 'CREATED', ?)
            """, orderNo, "订单创建");
        return ApiResponse.map("orderNo", orderNo, "status", "CREATED", "amount", amount);
    }

    /** 取消订单（仅待支付可取消） */
    @Transactional
    public Map<String, Object> cancel(String orderNo) {
        var rows = jdbc.queryForList(
                "SELECT * FROM t_biz_order WHERE order_no=? FOR UPDATE", orderNo);
        if (rows.isEmpty()) throw new IllegalArgumentException("订单不存在: " + orderNo);
        Map<String, Object> order = rows.get(0);
        if (!"CREATED".equals(order.get("status"))) {
            throw new IllegalStateException("仅待支付订单可取消, 当前: " + order.get("status"));
        }
        // 若存在进行中的支付单，先置失败（模拟关单）
        jdbc.update("""
            UPDATE t_pay_flow SET status='FAIL', fail_reason='订单取消, 主动关单', notify_time=?
            WHERE order_no=? AND status='PAYING'
            """, noGen.now(), orderNo);
        jdbc.update("""
            UPDATE t_biz_order SET status='CANCELED', cancel_time=? WHERE order_no=?
            """, noGen.now(), orderNo);
        jdbc.update("""
            INSERT INTO t_order_status_log(order_no, from_status, to_status, remark)
            VALUES (?, 'CREATED', 'CANCELED', ?)
            """, orderNo, "用户取消订单");
        return ApiResponse.map("orderNo", orderNo, "status", "CANCELED");
    }

    public List<Map<String, Object>> list(Long userId, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT order_no, user_id, title, amount, status, create_time, pay_time, cancel_time " +
                "FROM t_biz_order WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (userId != null) { sql.append("AND user_id=? "); args.add(userId); }
        if (status != null && !status.isBlank()) { sql.append("AND status=? "); args.add(status); }
        sql.append("ORDER BY id DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 订单详情：含关联支付流水 + 关联充值流水 + 状态流转日志 */
    public Map<String, Object> detail(String orderNo) {
        var rows = jdbc.queryForList("SELECT * FROM t_biz_order WHERE order_no=?", orderNo);
        if (rows.isEmpty()) return null;
        Map<String, Object> order = rows.get(0);
        Long userId = ((Number) order.get("user_id")).longValue();
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("payFlows", jdbc.queryForList(
                "SELECT pay_no, amount, channel, status, fail_reason, create_time, notify_time " +
                "FROM t_pay_flow WHERE order_no=? ORDER BY id DESC", orderNo));
        // 关联充值流水：同用户近期的充值（演示关联维度）
        result.put("rechargeFlows", jdbc.queryForList(
                "SELECT recharge_no, amount, channel, status, create_time " +
                "FROM t_recharge_flow WHERE user_id=? ORDER BY id DESC LIMIT 5", userId));
        result.put("statusLogs", jdbc.queryForList(
                "SELECT from_status, to_status, remark, create_time " +
                "FROM t_order_status_log WHERE order_no=? ORDER BY id", orderNo));
        return result;
    }
}
