plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.lespider.opinionflow"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

extra["springCloudAlibabaVersion"] = "2023.0.3.2"
extra["springCloudVersion"] = "2023.0.3"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:${property("springCloudAlibabaVersion")}")
    }
}

dependencies {
    // 公共模块
    implementation(project(":opinionflow-common"))

    // Feign 客户端声明（news / spider / company 等）
    implementation(project(":opinionflow-api"))

    // Spring Boot（MVC，配合 MCP 的 WebMvc 传输，避免 WebFlux 冲突）
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring AI MCP Server（Streamable SSE；版本由根 build.gradle 的 spring-ai-bom:1.0.0 管理）
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")

    // WebMvc（Servlet）SSE 传输实现：提供 io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider。
    // 说明：base 的 spring-ai-starter-mcp-server 只带核心 MCP SDK（默认回落 STDIO 传输），
    //       不引入本模块时 McpWebMvcServerAutoConfiguration 因 @ConditionalOnClass 不生效，
    //       服务能启动但不会暴露 HTTP /sse 端点（MCP Client 无法连接）。
    //       等价于官方 spring-ai-starter-mcp-server-webmvc 相比 base 多出的依赖，版本需与 spring-ai-mcp:1.0.0 内部一致（0.10.0）。
    implementation("io.modelcontextprotocol.sdk:mcp-spring-webmvc:0.10.0")

    // Spring Cloud Alibaba - Nacos 服务发现（MCP Server 注册到 Nacos，供 AI 侧寻址）
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery")

    // OpenFeign + LoadBalancer（访问 opinionflow-news / opinionflow-spider）
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")

    // Jackson datetime
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8"
    )
}
