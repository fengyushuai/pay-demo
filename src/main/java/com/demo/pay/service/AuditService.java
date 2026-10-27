package com.demo.pay.service;

import com.demo.pay.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据稽核模块：
 * 充值流水、支付流水、订单数据三方对账，自动识别差异并输出差异明细；
 * 稽核日志全程留存，可查看稽核结果。
 *
 * 对账规则（演示）：
 *  R1 订单已支付(PAID) 但无 SUCCESS 支付流水          -> 订单-支付 不一致
 *  R2 订单已支付 但支付流水金额与订单金额不一致        -> 金额差异
 *  R3 支付流水 SUCCESS 但关联订单不是 PAID            -> 支付-订单 不一致
 *  R4 支付流水 SUCCESS 但无关联订单                   -> 悬空支付
 *  R5 订单已取消(CANCELED) 但存在 SUCCESS 支付流水     -> 矛盾状态
 *  R6 充值 SUCCESS 但未回传业务前台(forward!=DONE)    -> 充值回传缺失
 *  R7 充值 PROCESSING 超过10分钟无回调                -> 渠道回调悬挂
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;
    private final NoGenerator noGen;

    public AuditService(JdbcTemplate jdbc, NoGenerator noGen) {
        this.jdbc = jdbc;
        this.noGen = noGen;
    }

    @Transactional
    public Map<String, Object> runAudit() {
        String taskNo = noGen.next("AD");
        jdbc.update("INSERT INTO t_audit_task(task_no, status) VALUES (?, 'RUNNING')", taskNo);
        Long taskId = jdbc.queryForObject(
                "SELECT id FROM t_audit_task WHERE task_no=?", Long.class, taskNo);
        List<String> logs = new ArrayList<>();
        List<Map<String, Object>> diffs = new ArrayList<>();

        log(logs, "稽核任务[" + taskNo + "] 启动三方对账: 充值流水 / 支付流水 / 业务订单");

        // R1: 已支付订单缺少成功支付流水
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT o.order_no biz_no, o.amount, o.user_id
            FROM t_biz_order o
            LEFT JOIN t_pay_flow p ON p.order_no=o.order_no AND p.status='SUCCESS'
            WHERE o.status='PAID' AND p.pay_no IS NULL
            """), "ORDER_MISSING_SUCCESS_PAY", "ORDER", "订单已支付但无成功支付流水", logs);

        // R2: 订单金额与成功支付金额不一致
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT o.order_no biz_no, o.amount, p.amount pay_amount, o.user_id
            FROM t_biz_order o
            JOIN t_pay_flow p ON p.order_no=o.order_no AND p.status='SUCCESS'
            WHERE o.status='PAID' AND o.amount <> p.amount
            """), "ORDER_PAY_AMOUNT_MISMATCH", "ORDER", "订单金额与支付流水金额不一致", logs);

        // R3: 成功支付流水但订单不是已支付
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT p.pay_no biz_no, p.order_no, p.amount, p.status pay_status, o.status order_status
            FROM t_pay_flow p
            LEFT JOIN t_biz_order o ON o.order_no=p.order_no
            WHERE p.status='SUCCESS' AND (o.status IS NULL OR o.status <> 'PAID')
            """), "PAY_SUCCESS_ORDER_NOT_PAID", "PAY", "支付成功但订单未置为已支付", logs);

        // R4: 悬空支付（无关联订单）
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT p.pay_no biz_no, p.amount
            FROM t_pay_flow p
            LEFT JOIN t_biz_order o ON o.order_no=p.order_no
            WHERE p.status='SUCCESS' AND o.order_no IS NULL
            """), "PAY_ORPHAN", "PAY", "成功支付流水无关联订单", logs);

        // R5: 已取消订单存在成功支付
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT o.order_no biz_no, p.pay_no
            FROM t_biz_order o
            JOIN t_pay_flow p ON p.order_no=o.order_no AND p.status='SUCCESS'
            WHERE o.status='CANCELED'
            """), "CANCELED_ORDER_HAS_PAY", "ORDER", "已取消订单存在成功支付流水", logs);

        // R6: 充值成功未回传业务前台
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT recharge_no biz_no, user_id, amount
            FROM t_recharge_flow
            WHERE status='SUCCESS' AND forward_status <> 'DONE'
            """), "RECHARGE_FORWARD_MISSING", "RECHARGE", "充值成功但未回传业务前台", logs);

        // R7: 充值处理中超过10分钟（渠道回调悬挂）
        addDiffs(diffs, taskId, jdbc.queryForList("""
            SELECT recharge_no biz_no, user_id, amount, create_time
            FROM t_recharge_flow
            WHERE status='PROCESSING' AND create_time < DATE_SUB(NOW(), INTERVAL 10 MINUTE)
            """), "RECHARGE_CALLBACK_HANG", "RECHARGE", "充值处理中超过10分钟未收到渠道回调", logs);

        int diffCount = diffs.size();
        String summary = String.format(
                "对账完成: 发现差异 %d 笔 (订单-支付 %d / 支付-订单 %d / 充值 %d)",
                diffCount,
                countType(diffs, "ORDER"),
                countType(diffs, "PAY"),
                countType(diffs, "RECHARGE"));

        LocalDateTime now = noGen.now();
        jdbc.update("""
            UPDATE t_audit_task SET status='DONE', diff_count=?, summary=?, finish_time=? WHERE id=?
            """, diffCount, summary, now, taskId);
        log(logs, summary);
        log(logs, "稽核任务[" + taskNo + "] 完成, 差异明细已留存, 共 " + diffCount + " 条");
        persistLogs(taskId, logs);   // 稽核日志留存

        return ApiResponse.map("taskNo", taskNo, "taskId", taskId,
                "diffCount", diffCount, "summary", summary, "diffs", diffs, "logs", logs);
    }

    private void addDiffs(List<Map<String, Object>> diffs, Long taskId, List<Map<String, Object>> rows,
                          String diffType, String bizType, String desc, List<String> logs) {
        logs.add("规则[" + diffType + "] " + desc + " -> 命中 " + rows.size() + " 笔");
        for (Map<String, Object> row : rows) {
            String bizNo = String.valueOf(row.get("biz_no"));
            String detail = desc + " | " + row;
            jdbc.update("""
                INSERT INTO t_audit_diff(task_id, diff_type, biz_type, biz_no, detail)
                VALUES (?,?,?,?,?)
                """, taskId, diffType, bizType, bizNo, detail);
            diffs.add(Map.of("diffType", diffType, "bizType", bizType,
                    "bizNo", bizNo, "detail", detail));
        }
    }

    private int countType(List<Map<String, Object>> diffs, String bizType) {
        return (int) diffs.stream().filter(d -> bizType.equals(d.get("bizType"))).count();
    }

    private void log(List<String> logs, String content) {
        logs.add(content);
    }

    /** 稽核日志落库（留存） */
    private void persistLogs(Long taskId, List<String> logs) {
        for (String c : logs) {
            jdbc.update(
                "INSERT INTO t_audit_log(task_id, log_level, content) VALUES (?, 'INFO', ?)",
                taskId, c);
        }
    }

    /** 稽核任务列表 */
    public List<Map<String, Object>> tasks() {
        return jdbc.queryForList(
                "SELECT task_no, status, diff_count, summary, create_time, finish_time " +
                "FROM t_audit_task ORDER BY id DESC LIMIT 50");
    }

    /** 稽核结果详情: 差异明细 + 稽核日志 */
    public Map<String, Object> detail(String taskNo) {
        var tasks = jdbc.queryForList("SELECT * FROM t_audit_task WHERE task_no=?", taskNo);
        if (tasks.isEmpty()) return null;
        Long taskId = ((Number) tasks.get(0).get("id")).longValue();
        Map<String, Object> result = new HashMap<>();
        result.put("task", tasks.get(0));
        result.put("diffs", jdbc.queryForList(
                "SELECT diff_type, biz_type, biz_no, detail, create_time " +
                "FROM t_audit_diff WHERE task_id=? ORDER BY id", taskId));
        result.put("logs", jdbc.queryForList(
                "SELECT log_level, content, create_time FROM t_audit_log WHERE task_id=? ORDER BY id", taskId));
        return result;
    }
}
