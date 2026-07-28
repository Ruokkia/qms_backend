package com.kangli.qms.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>启用分页插件（PostgreSQL 方言）与乐观锁插件（@Version 字段自动版本校验/递增）。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
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
