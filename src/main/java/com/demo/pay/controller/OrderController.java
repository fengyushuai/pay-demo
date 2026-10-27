package com.demo.pay.controller;

import com.demo.pay.model.ApiResponse;
import com.demo.pay.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 订单模块接口 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public ApiResponse create(@RequestBody Map<String, Object> req) {
        Long userId = Long.valueOf(String.valueOf(req.get("userId")));
        String title = String.valueOf(req.getOrDefault("title", "演示订单"));
        double amount = Double.parseDouble(String.valueOf(req.get("amount")));
        try {
            return ApiResponse.ok(service.create(userId, title, amount));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse cancel(@PathVariable String orderNo) {
        try {
            return ApiResponse.ok(service.cancel(orderNo));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse list(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(userId, status));
    }

    /** 订单详情（含关联支付/充值流水与状态日志） */
    @GetMapping("/{orderNo}")
    public ApiResponse detail(@PathVariable String orderNo) {
        Map<String, Object> d = service.detail(orderNo);
        return d == null ? ApiResponse.fail("订单不存在") : ApiResponse.ok(d);
    }
}
