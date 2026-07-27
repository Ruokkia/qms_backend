package com.kangli.qms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 康力质量管理系统（QMS）后端启动类。
 */
@SpringBootApplication
@MapperScan("com.kangli.qms.mapper")
public class QmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(QmsApplication.class, args);
    }
}
