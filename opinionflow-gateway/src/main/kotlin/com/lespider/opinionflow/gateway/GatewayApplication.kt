package com.lespider.opinionflow.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

/**
 * 网关服务启动类
 * 
 * 功能：
 * 1. API 路由转发 - 将前端请求路由到对应的微服务
 * 2. 跨域处理 - 统一 CORS 配置
 * 3. 服务发现 - 通过 Nacos 自动发现后端服务
 */
@SpringBootApplication
@EnableDiscoveryClient
class GatewayApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<GatewayApplication>(*args)
        }
    }
}
