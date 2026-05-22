package com.lespider.opinionflow.news

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@ComponentScan(basePackages = ["com.lespider.opinionflow"])
class NewsApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<NewsApplication>(*args)
        }
    }
}
