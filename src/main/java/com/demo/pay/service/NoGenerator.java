package com.demo.pay.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** 单号生成器 */
@Service
public class NoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbc;

    public NoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 生成业务单号：前缀 + 时间戳 + 4位序列（取自当前毫秒+随机数，演示环境够用） */
    public synchronized String next(String prefix) {
        String ts = LocalDateTime.now().format(FMT);
        int seq = ThreadLocalRandom.current().nextInt(1000, 9999);
        return prefix + ts + seq;
    }

    /** 数据库当前时间（统一时钟，避免应用服务器时间漂移影响稽核） */
    public LocalDateTime now() {
        return jdbc.queryForObject("SELECT NOW()", LocalDateTime.class);
    }
}
