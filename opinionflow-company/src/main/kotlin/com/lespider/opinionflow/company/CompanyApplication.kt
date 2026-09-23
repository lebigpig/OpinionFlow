package com.lespider.opinionflow.company

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

/**
 * 企业服务启动类
 * 端口：9206
 * 数据源：company_china（中国企业财报数据）
 */
@EnableDiscoveryClient
@SpringBootApplication(
    // 多数据源（company_china + company_us）：两套 DataSource / EntityManagerFactory / 仓库均手动装配，
    // 见 config/ChinaDataSourceConfig.kt 与 config/UsDataSourceConfig.kt，
    // 故排除 Spring Boot 的单数据源自动配置，避免 EMF 相互覆盖。
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        JpaRepositoriesAutoConfiguration::class,
    ],
    scanBasePackages = ["com.lespider.opinionflow.company", "com.lespider.opinionflow.common"],
)
class CompanyApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CompanyApplication>(*args)
        }
    }
}