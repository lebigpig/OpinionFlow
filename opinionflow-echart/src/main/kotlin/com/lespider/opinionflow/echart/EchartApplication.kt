package com.lespider.opinionflow.echart

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

/**
 * 图表服务启动类
 * 端口：9204
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = ["com.lespider.opinionflow.echart", "com.lespider.opinionflow.common"])
class EchartApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<EchartApplication>(*args)
        }
    }
}
