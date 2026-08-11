package com.kangli.qms.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.kangli.qms.domain.fai.handler.JsonbTypeHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * MyBatis-Plus 配置。
 * <p>启用分页插件（PostgreSQL 方言）与乐观锁插件（@Version 字段自动版本校验/递增）。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 全局注册 PostgreSQL jsonb 类型处理器，替代默认的 JacksonTypeHandler（后者用 setString 导致 PG 报类型不匹配）。
     * 使用 {@link ApplicationReadyEvent} 监听器在 Spring 容器初始化完成后注册，避免循环依赖。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerJsonbTypeHandler(ApplicationReadyEvent event) {
        SqlSessionFactory sqlSessionFactory = event.getApplicationContext().getBean(SqlSessionFactory.class);
        sqlSessionFactory.getConfiguration().getTypeHandlerRegistry()
                .register(JsonNode.class, new JsonbTypeHandler());
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分公司数据隔离：基于 TenantLineInnerInterceptor 为所有单表 SELECT/UPDATE/DELETE
        // 自动注入 plant_code 过滤（修复 L6 越权问题），须位于分页/乐观锁之前。
        interceptor.addInnerInterceptor(new PlantTenantInterceptor());
        // 乐观锁：所有表 version 字段的自动校验与递增（缺失会导致 updateById 报
        // Parameter 'MP_OPTLOCK_VERSION_ORIGINAL' not found）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 单页最大 100 条，防止超大查询
        pageInterceptor.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;
    }
}
