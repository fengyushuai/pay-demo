package com.demo.pay.controller;

import com.demo.pay.model.ApiResponse;
import com.demo.pay.service.AuditService;
import org.springframework.web.bind.annotation.*;

/** 数据稽核模块接口 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** 执行一次三方对账 */
    @PostMapping("/run")
    public ApiResponse run() {
        return ApiResponse.ok(service.runAudit());
    }

    /** 稽核任务列表 */
    @GetMapping("/tasks")
    public ApiResponse tasks() {
        return ApiResponse.ok(service.tasks());
    }

    /** 稽核结果详情（差异明细 + 稽核日志） */
    @GetMapping("/tasks/{taskNo}")
    public ApiResponse detail(@PathVariable String taskNo) {
        var d = service.detail(taskNo);
        return d == null ? ApiResponse.fail("稽核任务不存在") : ApiResponse.ok(d);
    }
}
