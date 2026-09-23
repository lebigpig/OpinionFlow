package com.lespider.opinionflow.company.config

import jakarta.persistence.EntityManagerFactory
import java.util.Properties
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager

/**
 * 数据源 1（主）：中国企业财报库 company_china
 *
 * 多数据源方案说明：
 * - 本类显式提供 company_china 的 DataSource / EntityManagerFactory / TransactionManager（均 @Primary）
 *   并只扫描 [com.lespider.opinionflow.company.domain] 与 [com.lespider.opinionflow.company.repo]
 * - 美股数据源见 [UsDataSourceConfig]（company_us 库，独立 EMF / 独立事务管理器）
 * - 相应的 Spring Boot 自动配置已在 CompanyApplication 中排除，避免两套 EMF 相互覆盖
 */
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.lespider.opinionflow.company.repo"],
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager",
)
class ChinaDataSourceConfig(
    @Value("\${spring.datasource.url}") private val url: String,
    @Value("\${spring.datasource.username}") private val username: String,
    @Value("\${spring.datasource.password}") private val password: String,
    @Value("\${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") private val driverClassName: String,
    @Value("\${spring.jpa.hibernate.ddl-auto:none}") private val ddlAuto: String,
    @Value("\${spring.jpa.show-sql:false}") private val showSql: Boolean,
    @Value("\${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.MySQLDialect}") private val dialect: String,
) {

    /** 主数据源：company_china（中国企业财报数据） */
    @Primary
    @Bean(name = ["dataSource"])
    fun dataSource(): DataSource = DataSourceBuilder.create()
        .url(url)
        .username(username)
        .password(password)
        .driverClassName(driverClassName)
        .build()

    /** 主实体管理器工厂：仅扫描中国企业财报实体（com.lespider.opinionflow.company.domain） */
    @Primary
    @Bean(name = ["entityManagerFactory"])
    fun entityManagerFactory(
        @Qualifier("dataSource") dataSource: DataSource,
    ): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = dataSource
        factory.setPackagesToScan("com.lespider.opinionflow.company.domain")
        factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
        factory.setJpaProperties(hibernateProperties())
        return factory
    }

    /** 主事务管理器（China 库） */
    @Primary
    @Bean(name = ["transactionManager"])
    fun transactionManager(
        @Qualifier("entityManagerFactory") entityManagerFactory: EntityManagerFactory,
    ): PlatformTransactionManager = JpaTransactionManager(entityManagerFactory)

    private fun hibernateProperties(): Properties = Properties().apply {
        this["hibernate.dialect"] = dialect
        this["hibernate.hbm2ddl.auto"] = ddlAuto
        this["hibernate.show_sql"] = showSql.toString()
    }
}
