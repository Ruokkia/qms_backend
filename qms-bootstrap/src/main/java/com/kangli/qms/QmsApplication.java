package com.kangli.qms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 康力质量管理系统（QMS）后端启动类。
 */
@SpringBootApplication
@MapperScan("com.kangli.qms.domain")
@EnableScheduling
public class QmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(QmsApplication.class, args);
    }
}
