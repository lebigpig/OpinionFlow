package com.lespider.opinionflow.company

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

/**
 * 企业服务启动类
 * 端口：9206
 * 数据源：company_china（中国企业财报数据）
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = ["com.lespider.opinionflow.company", "com.lespider.opinionflow.common"])
class CompanyApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CompanyApplication>(*args)
        }
    }
}