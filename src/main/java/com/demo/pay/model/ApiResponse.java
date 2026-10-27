package com.demo.pay.model;

import java.util.HashMap;
import java.util.Map;

/** 统一响应结构 */
public class ApiResponse {
    public int code;            // 0=成功，非0=失败
    public String message;
    public Object data;

    public static ApiResponse ok(Object data) {
        ApiResponse r = new ApiResponse();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static ApiResponse ok() {
        return ok(null);
    }

    public static ApiResponse fail(String message) {
        ApiResponse r = new ApiResponse();
        r.code = 1;
        r.message = message;
        return r;
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
