package com.kangli.qms.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.kangli.qms.common.LoginUserHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 分公司数据隔离拦截器（安全审计 L6 的根因修复）。
 * <p>
 * 基于 MyBatis-Plus 的 {@link TenantLineInnerInterceptor} 改写 SQL，为所有单表
 * SELECT/UPDATE/DELETE（含 selectById/updateById/deleteById）自动追加
 * <code>AND plant_code = ?</code>，从而从基础设施层统一补齐 by-ID 操作长期缺失的
 * 越权过滤，避免在每个 Service 逐个手写 .eq(plantCode)。
 * </p>
 * <p>
 * 生效厂取值：{@link LoginUserHolder#get()} 中的 {@code plantCode}。对于拥有
 * ALL_PLANTS 角色的管理员，{@code JwtInterceptor} 已按 {@code X-Plant-Code} 头将其
 * 改写成"生效厂"，因此单请求单厂，不会跨区域混查。
 * </p>
 * <p>
 * 跳过规则：
 * <ul>
 *   <li>LoginUserHolder 为空（如 JwtInterceptor 自身查库、定时任务/内部调用）：不注入，
 *       避免 NPE 与误过滤；</li>
 *   <li>无 plant_code 列的全局表（spc_coefficient/sys_module/sys_role_permission）：ignoreTable 排除；</li>
 *   <li>INSERT 不自动注入 plant_code（交由实体自身携带，避免写入无此列的表报错）；</li>
 *   <li>含 JOIN 的报表方法通过 {@code @InterceptorIgnore(tenantLine = true)} 跳过，
 *       保留其 XML 中已手写的 plant_code 过滤，避免多表列歧义。</li>
 * </ul>
 * </p>
 */
public class PlantTenantInterceptor implements InnerInterceptor {

    /**
     * 无 plant_code 列、或语义为全局不应按厂过滤的表，注入会导致 SQL 报错或丢失全局数据，必须排除。
     * 业务表均已含 plant_code 列（现有 list/page 早已手写 .eq(plantCode) 过滤，无列会在历史上就报错），无需列入。
     */
    private static final Set<String> IGNORE_TABLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "spc_coefficient",        // 无 plant_code 列
                    "sys_module",             // 无 plant_code 列（框架菜单表）
                    "sys_role_permission",     // 无 plant_code 列（角色-权限关联表）
                    "sys_role",               // 全局角色，plant_code='*'，按厂过滤会丢失全局角色
                    "sys_user",               // 全局用户表，按厂过滤会导致跨厂区鉴权失败（如电子签名验密、权限校验）
                    "sys_role_user",          // 角色-用户关联表，跨厂区鉴权必须
                    "notification_config",    // 全局通知场景配置，无 plant_code 列
                    "qms_migration_log"       // 无 plant_code 列（Flyway 迁移日志）
            )));

    private final TenantLineInnerInterceptor delegate = new TenantLineInnerInterceptor();

    public PlantTenantInterceptor() {
        delegate.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new StringValue(LoginUserHolder.get().getPlantCode().name());
            }

            @Override
            public String getTenantIdColumn() {
                return "plant_code";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return IGNORE_TABLES.contains(tableName);
            }
        });
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 上下文为空：内部任务/JwtInterceptor 自身查库，不注入 plant_code
        if (LoginUserHolder.get() == null) {
            return;
        }
        delegate.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        if (LoginUserHolder.get() == null) {
            return;
        }
        // INSERT 不自动注入 plant_code：避免写入无此列的表导致 SQL 错误；
        // 业务实体在 Service 层已自行携带 plantCode 字段。
        if (ms.getSqlCommandType() == SqlCommandType.INSERT) {
            return;
        }
        delegate.beforeUpdate(executor, ms, parameter);
    }
}
