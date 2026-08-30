package com.lespider.opinionflow.news.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 将 Tomcat 配置属性暴露为 Prometheus 指标
 */
@Configuration
class TomcatMetricsConfig {

    @Bean
    fun tomcatConfigMetrics(serverProperties: ServerProperties): MeterBinder {
        return MeterBinder { registry: MeterRegistry ->
            val tomcat = serverProperties.tomcat
            val threads = tomcat?.threads

            // server.tomcat.threads.max -> tomcat.threads.config.max
            threads?.max?.let { maxThreads ->
                Gauge.builder("tomcat.threads.config.max") { maxThreads.toDouble() }
                    .description("The maximum number of worker threads")
                    .register(registry)
            }

            // server.tomcat.max-connections -> tomcat.connections.max
            tomcat?.maxConnections?.let { maxConn ->
                Gauge.builder("tomcat.connections.max") { maxConn.toDouble() }
                    .description("Maximum number of connections that the server will accept")
                    .register(registry)
            }

            // server.tomcat.accept-count -> tomcat.connections.accept.count
            tomcat?.acceptCount?.let { acceptCount ->
                Gauge.builder("tomcat.connections.accept.count") { acceptCount.toDouble() }
                    .description("Maximum queue length for incoming connection requests")
                    .register(registry)
            }
        }
    }
}