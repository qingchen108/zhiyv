package com.smartmed.backend.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置（CONTEXT §2）。
 * <p>
 * 02 阶段仅配分页插件。{@code BaseEntity} + {@code MetaObjectHandler} 自动填充留待 03
 * （碰 department/doctor 等业务表时再引入）。
 * <p>
 * 06 起引入 Neo4j（spring-boot-starter-data-neo4j）。其 {@code Neo4jTransactionManagerConfiguration}
 * 会创建名为 {@code transactionManager} 的 {@code Neo4jTransactionManager}（PlatformTransactionManager），
 * 同时 reactive 链创建 {@code reactiveTransactionManager}，导致 JDBC 的
 * {@code DataSourceTransactionManager} 不创建（{@code @ConditionalOnMissingBean} 被跳过），
 * {@code @Transactional} 在两个 Neo4j TX 间歧义（NoUniqueBeanDefinitionException）。
 * <p>
 * 此处显式声明 JDBC 事务管理器，bean 名 {@code transactionManager} 优先于 Neo4j 的同名 bean
 * （{@code Neo4jTransactionManagerConfiguration} 的 {@code @ConditionalOnMissingBean} 跳过），
 * 并标 {@code @Primary} 消除与 reactive TX 的歧义。{@code Neo4jClient} 仍由
 * {@code Neo4jTransactionalComponentsConfiguration} 创建（其 {@code @ConditionalOnBean(PlatformTransactionManager)}
 * 由本 bean 满足，ADR-0012：Neo4j 仅只读 Cypher 查询）。
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /** JDBC 事务管理器（bean 名 transactionManager 覆盖 Neo4j 同名 bean，@Primary 消除 reactive 歧义）。 */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
