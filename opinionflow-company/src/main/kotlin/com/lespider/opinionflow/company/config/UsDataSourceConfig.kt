package com.lespider.opinionflow.company.config

import jakarta.persistence.EntityManagerFactory
import java.util.Properties
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager

/**
 * 数据源 2：美国企业财报库 company_us（美股 / US GAAP / SEC EDGAR）
 *
 * - 由本类提供独立的 DataSource / EntityManagerFactory / TransactionManager（不加 @Primary）
 * - 只扫描 [com.lespider.opinionflow.company.us.domain] 与 [com.lespider.opinionflow.company.us.repo]
 * - 使用方：CompanyUsController / CompanyUsService（REST 前缀 /api/company/us/ ）
 */
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.lespider.opinionflow.company.us.repo"],
    entityManagerFactoryRef = "usEntityManagerFactory",
    transactionManagerRef = "usTransactionManager",
)
class UsDataSourceConfig(
    @Value("\${spring.datasource.us.url}") private val url: String,
    @Value("\${spring.datasource.us.username}") private val username: String,
    @Value("\${spring.datasource.us.password}") private val password: String,
    @Value("\${spring.datasource.us.driver-class-name:com.mysql.cj.jdbc.Driver}") private val driverClassName: String,
    @Value("\${spring.jpa.hibernate.ddl-auto:none}") private val ddlAuto: String,
    @Value("\${spring.jpa.show-sql:false}") private val showSql: Boolean,
    @Value("\${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.MySQLDialect}") private val dialect: String,
) {

    /** 美股数据源：company_us */
    @Bean(name = ["usDataSource"])
    fun usDataSource(): DataSource = DataSourceBuilder.create()
        .url(url)
        .username(username)
        .password(password)
        .driverClassName(driverClassName)
        .build()

    /** 美股实体管理器工厂：仅扫描美股实体（com.lespider.opinionflow.company.us.domain） */
    @Bean(name = ["usEntityManagerFactory"])
    fun usEntityManagerFactory(
        @Qualifier("usDataSource") dataSource: DataSource,
    ): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = dataSource
        factory.setPackagesToScan("com.lespider.opinionflow.company.us.domain")
        factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
        factory.setJpaProperties(hibernateProperties())
        return factory
    }

    /** 美股事务管理器（US 库），US 服务需显式指定 transactionManager = "usTransactionManager" */
    @Bean(name = ["usTransactionManager"])
    fun usTransactionManager(
        @Qualifier("usEntityManagerFactory") entityManagerFactory: EntityManagerFactory,
    ): PlatformTransactionManager = JpaTransactionManager(entityManagerFactory)

    private fun hibernateProperties(): Properties = Properties().apply {
        this["hibernate.dialect"] = dialect
        this["hibernate.hbm2ddl.auto"] = ddlAuto
        this["hibernate.show_sql"] = showSql.toString()
    }
}
