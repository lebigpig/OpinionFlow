package com.lespider.opinionflow.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.lespider.opinionflow.common", "com.lespider.opinionflow.ai"])
@EnableDiscoveryClient
@EnableFeignClients(basePackages = ["com.lespider.opinionflow.api"])
@EnableJpaRepositories(basePackages = ["com.lespider.opinionflow.ai.repo"])
class AiApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<AiApplication>(*args)
        }
    }
}
