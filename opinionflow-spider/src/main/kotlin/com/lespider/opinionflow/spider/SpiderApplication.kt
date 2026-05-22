package com.lespider.opinionflow.spider

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

@SpringBootApplication(scanBasePackages = ["com.lespider.opinionflow.common", "com.lespider.opinionflow.spider"])
@EnableDiscoveryClient
class SpiderApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<SpiderApplication>(*args)
        }
    }
}
