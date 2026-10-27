# 充值·支付·订单·数据稽核 演示系统

技术栈：**SpringBoot 3.2 + MySQL 8 + jQuery**（前端纯 JS/jQuery，无框架）

## 四大模块（对应演示评分点）

| 模块 | 演示能力 |
|---|---|
| ① 充值 | 用户发起充值 → 渠道受理（模拟回调）→ **余额实时入账**；充值流水生成与查询；充值结果**同步回传业务前台**（forward_status 记录回传动作） |
| ② 支付 | **订单唤起支付**；**多支付通道路由选择**（AUTO 按金额自动路由 / 手动指定：`<100` 微信、`100~5000` 支付宝、`>5000` 银联）；支付请求下发；**异步回调**（可模拟成功/失败）；支付状态更新，失败场景订单保持待支付可重新发起 |
| ③ 订单 | 创建业务订单；**全生命周期状态** CREATED(待支付)/PAID(已支付)/CANCELED(已取消)；订单查询；**关联充值/支付流水**；**状态变更追溯**（t_order_status_log 全量留痕） |
| ④ 数据稽核 | 充值流水、支付流水、订单**三方对账**；**7 类差异自动识别**并输出差异明细；**稽核日志留存**（t_audit_log）；可查看历史稽核结果 |

稽核规则清单：R1 已支付订单无成功支付 · R2 订单支付金额不一致 · R3 支付成功订单未置 PAID · R4 悬空支付 · R5 取消订单存在成功支付 · R6 充值成功未回传前台 · R7 渠道回调悬挂(>10分钟)

## 运行步骤

1. 准备 MySQL 8，确保 `root/root` 可连本机 3306（账号在 `application.yml` 中修改）
2. 无需手工建库建表，启动时自动执行 `sql/schema.sql`（幂等 `IF NOT EXISTS`）与种子数据
3. 启动：

```bash
mvn spring-boot:run
```

4. 打开演示页面：http://localhost:8080/index.html

## 推荐演示路径

1. **充值**：选用户 → 输金额发起充值 → 流水 PROCESSING → 点"回调成功" → 余额实时增加、回传状态 DONE（也可点"回调失败"看失败场景）
2. **订单**：创建订单 → 列表点"去支付"跳到支付页
3. **支付**：通道选 AUTO 下发 → 点"回调成功" → 支付 SUCCESS 且订单自动置 PAID（点"回调失败"则订单保持待支付，可再次发起）
4. **稽核**：点"立即执行稽核" → 输出差异明细与稽核日志（可人为制造差异：如直接把某订单 status 改成 PAID 而不回调支付，稽核会命中 R1）

## 接口清单

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 充值 | POST | /api/recharge/initiate | `{userId, amount, channel}` 发起充值 |
| 充值 | POST | /api/recharge/{no}/channelNotify?result=success\|fail | 模拟渠道回调 |
| 充值 | GET | /api/recharge/list?userId=&status= | 流水查询 |
| 充值 | GET | /api/recharge/balance/{userId} | 余额查询 |
| 支付 | POST | /api/pay/create | `{orderNo, channel}` channel=AUTO 自动路由 |
| 支付 | POST | /api/pay/{no}/notify?result=success\|fail | 模拟第三方异步回调 |
| 支付 | GET | /api/pay/list?orderNo=&status= | 支付流水 |
| 订单 | POST | /api/order/create | `{userId, title, amount}` |
| 订单 | POST | /api/order/{no}/cancel | 取消订单（联动关单） |
| 订单 | GET | /api/order/list、/api/order/{no} | 列表/详情（含流水与状态日志） |
| 稽核 | POST | /api/audit/run | 执行三方对账 |
| 稽核 | GET | /api/audit/tasks、/api/audit/tasks/{no} | 任务列表/结果详情 |
