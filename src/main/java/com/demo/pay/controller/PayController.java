package com.demo.pay.controller;

import com.demo.pay.model.ApiResponse;
import com.demo.pay.service.PayService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 支付模块接口 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService service;

    public PayController(PayService service) {
        this.service = service;
    }

    /** 订单唤起支付（channel=AUTO 表示自动路由） */
    @PostMapping("/create")
    public ApiResponse create(@RequestBody Map<String, Object> req) {
        String orderNo = String.valueOf(req.get("orderNo"));
        String channel = String.valueOf(req.getOrDefault("channel", "AUTO"));
        try {
            return ApiResponse.ok(service.createPay(orderNo, channel));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 模拟第三方异步回调（result=success|fail） */
    @PostMapping("/{payNo}/notify")
    public ApiResponse notify(@PathVariable String payNo,
                              @RequestParam(defaultValue = "success") String result,
                              @RequestParam(required = false) String reason) {
        try {
            return ApiResponse.ok(service.notify(payNo, result, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 支付流水查询 */
    @GetMapping("/list")
    public ApiResponse list(@RequestParam(required = false) String orderNo,
                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(orderNo, status));
    }
}
