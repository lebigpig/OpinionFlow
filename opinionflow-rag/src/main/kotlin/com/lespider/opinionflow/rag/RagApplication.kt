package com.lespider.opinionflow.rag

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
class RagApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<RagApplication>(*args)
        }
    }
}
