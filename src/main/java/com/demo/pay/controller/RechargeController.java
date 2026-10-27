package com.demo.pay.controller;

import com.demo.pay.model.ApiResponse;
import com.demo.pay.service.RechargeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 充值模块接口 */
@RestController
@RequestMapping("/api/recharge")
public class RechargeController {

    private final RechargeService service;

    public RechargeController(RechargeService service) {
        this.service = service;
    }

    /** 用户发起充值 */
    @PostMapping("/initiate")
    public ApiResponse initiate(@RequestBody Map<String, Object> req) {
        Long userId = Long.valueOf(String.valueOf(req.get("userId")));
        double amount = Double.parseDouble(String.valueOf(req.get("amount")));
        String channel = String.valueOf(req.getOrDefault("channel", "WECHAT"));
        try {
            return ApiResponse.ok(service.initiate(userId, amount, channel));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 模拟充值渠道回调（success / fail） */
    @PostMapping("/{rechargeNo}/channelNotify")
    public ApiResponse channelNotify(@PathVariable String rechargeNo,
                                     @RequestParam(defaultValue = "success") String result,
                                     @RequestParam(required = false) String reason) {
        try {
            return ApiResponse.ok(service.channelNotify(rechargeNo, result, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 充值流水查询 */
    @GetMapping("/list")
    public ApiResponse list(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(userId, status));
    }

    /** 充值单详情 */
    @GetMapping("/{rechargeNo}")
    public ApiResponse detail(@PathVariable String rechargeNo) {
        Map<String, Object> d = service.detail(rechargeNo);
        return d == null ? ApiResponse.fail("充值单不存在") : ApiResponse.ok(d);
    }

    /** 用户余额 */
    @GetMapping("/balance/{userId}")
    public ApiResponse balance(@PathVariable Long userId) {
        return ApiResponse.ok(service.balance(userId));
    }
}
