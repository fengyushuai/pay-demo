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
 * 支付模块：
 * 1. 订单唤起支付（校验订单状态）
 * 2. 多支付通道路由选择（AUTO 自动路由 / MANUAL 指定通道）
 * 3. 支付请求下发（生成 PAYING 流水）
 * 4. 支付结果异步回调（演示接口模拟第三方）-> 支付状态更新 -> 联动订单状态
 * 5. 支付成功/失败场景均可演示
 */
@Service
public class PayService {

    private final JdbcTemplate jdbc;
    private final NoGenerator noGen;

    public PayService(JdbcTemplate jdbc, NoGenerator noGen) {
        this.jdbc = jdbc;
        this.noGen = noGen;
    }

    /**
     * 多通道路由规则（演示）：
     * AUTO 模式下：金额 < 100 走 WECHAT；100~5000 走 ALIPAY；>5000 走 UNIONPAY（大额走网银）
     */
    String routeChannel(double amount, String channel) {
        if (channel != null && !"AUTO".equalsIgnoreCase(channel)) {
            return channel.toUpperCase();
        }
        if (amount < 100) return "WECHAT";
        if (amount <= 5000) return "ALIPAY";
        return "UNIONPAY";
    }

    /** 订单唤起支付 */
    @Transactional
    public Map<String, Object> createPay(String orderNo, String channel) {
        var orders = jdbc.queryForList(
                "SELECT * FROM t_biz_order WHERE order_no=? FOR UPDATE", orderNo);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("订单不存在: " + orderNo);
        }
        Map<String, Object> order = orders.get(0);
        String orderStatus = (String) order.get("status");
        if (!"CREATED".equals(orderStatus)) {
            throw new IllegalStateException("订单状态不允许支付, 当前: " + orderStatus);
        }
        // 幂等：存在进行中的支付单则直接返回
        var paying = jdbc.queryForList(
                "SELECT pay_no FROM t_pay_flow WHERE order_no=? AND status='PAYING'", orderNo);
        if (!paying.isEmpty()) {
            return ApiResponse.map("payNo", paying.get(0).get("pay_no"),
                    "status", "PAYING", "message", "该订单存在进行中的支付单, 已复用");
        }
        double amount = ((Number) order.get("amount")).doubleValue();
        Long userId = ((Number) order.get("user_id")).longValue();
        String routed = routeChannel(amount, channel);
        String payNo = noGen.next("PP");
        jdbc.update("""
            INSERT INTO t_pay_flow(pay_no, order_no, user_id, amount, channel, route_type, status, notify_content)
            VALUES (?,?,?,?,?,?,'PAYING', ?)
            """, payNo, orderNo, userId, amount, routed,
                "AUTO".equalsIgnoreCase(channel) ? "AUTO" : "MANUAL",
                "支付请求已下发给通道[" + routed + "], 第三方交易号=" + UUID.randomUUID().toString().substring(0, 12));
        return ApiResponse.map("payNo", payNo, "orderNo", orderNo, "channel", routed,
                "amount", amount, "status", "PAYING", "message", "支付请求已下发, 等待异步回调");
    }

    /**
     * 模拟第三方异步回调。
     * 成功：支付流水 SUCCESS + 订单置 PAID + 记录状态日志
     * 失败：支付流水 FAIL（订单保持 CREATED，可再次发起支付）
     */
    @Transactional
    public Map<String, Object> notify(String payNo, String result, String reason) {
        var rows = jdbc.queryForList("SELECT * FROM t_pay_flow WHERE pay_no=? FOR UPDATE", payNo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("支付单不存在: " + payNo);
        }
        Map<String, Object> flow = rows.get(0);
        String status = (String) flow.get("status");
        if (!"PAYING".equals(status)) {
            throw new IllegalStateException("支付单状态不允许回调, 当前: " + status);
        }
        String orderNo = (String) flow.get("order_no");
        LocalDateTime now = noGen.now();
        if ("success".equalsIgnoreCase(result)) {
            jdbc.update("""
                UPDATE t_pay_flow SET status='SUCCESS', notify_time=?, notify_content=? WHERE pay_no=?
                """, now, "异步回调成功, 第三方流水=" + UUID.randomUUID(), payNo);
            // 联动订单状态（仅 CREATED -> PAID）
            jdbc.update("""
                UPDATE t_biz_order SET status='PAID', pay_time=? WHERE order_no=? AND status='CREATED'
                """, now, orderNo);
            jdbc.update("""
                INSERT INTO t_order_status_log(order_no, from_status, to_status, remark)
                SELECT order_no, 'CREATED', 'PAID', ? FROM t_pay_flow WHERE pay_no=?
                """, "支付回调成功, 支付单=" + payNo, payNo);
            return ApiResponse.map("payNo", payNo, "status", "SUCCESS", "orderNo", orderNo);
        } else {
            jdbc.update("""
                UPDATE t_pay_flow SET status='FAIL', notify_time=?, fail_reason=?, notify_content=?
                WHERE pay_no=?
                """, now, reason == null ? "第三方回调失败" : reason, "异步回调失败: " + reason, payNo);
            jdbc.update("""
                INSERT INTO t_order_status_log(order_no, from_status, to_status, remark)
                VALUES (?, 'CREATED', 'CREATED', ?)
                """, orderNo, "支付失败(" + reason + "), 订单保持待支付, 支付单=" + payNo);
            return ApiResponse.map("payNo", payNo, "status", "FAIL", "orderNo", orderNo);
        }
    }

    public List<Map<String, Object>> list(String orderNo, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT pay_no, order_no, user_id, amount, channel, route_type, status, " +
                "fail_reason, create_time, notify_time FROM t_pay_flow WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (orderNo != null && !orderNo.isBlank()) { sql.append("AND order_no=? "); args.add(orderNo); }
        if (status != null && !status.isBlank()) { sql.append("AND status=? "); args.add(status); }
        sql.append("ORDER BY id DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
