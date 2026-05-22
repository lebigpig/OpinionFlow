package com.lespider.opinionflow.rag.config

import io.milvus.client.MilvusServiceClient
import io.milvus.param.ConnectParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["milvus.enabled"], havingValue = "true", matchIfMissing = false)
open class MilvusConfig {

    @Value("\${milvus.host:127.0.0.1}")
    private lateinit var host: String

    @Value("\${milvus.port:19530}")
    private var port: Int = 19530

    @Bean(destroyMethod = "close")
    fun milvusClient(): MilvusServiceClient {
        return MilvusServiceClient(
            ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build()
        )
    }
}
