plugins {
    kotlin("jvm") version "2.1.20"                 // 2026年4月最新稳定版
    kotlin("plugin.spring") version "2.1.20"
    kotlin("plugin.jpa") version "2.1.20"
    id("org.springframework.boot") version "3.4.5"  // 2026年4月最新稳定版
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lespider"
version = "0.0.1-SNAPSHOT"
description = "OpinionFlow"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.mysql:mysql-connector-j")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // LangChain4j Core – 提供消息类型、StreamingResponseHandler 等核心抽象
    implementation("dev.langchain4j:langchain4j-core:1.0.0-beta1")
    // LangChain4j – 提供 MessageWindowChatMemory 等默认实现
    implementation("dev.langchain4j:langchain4j:1.0.0-beta1")
    // LangChain4j OpenAI – 提供 OpenAiStreamingChatModel，替代手搓 HTTP+SSE
    implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta1")
    // Milvus Java SDK – 向量数据库客户端
    implementation("io.milvus:milvus-sdk-java:2.4.6")
    // Protobuf – Milvus SDK 运行时依赖
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    // LangChain4j Embeddings – 文本向量化基础
    implementation("dev.langchain4j:langchain4j-embeddings:1.0.0-beta1")
    // LangChain4j Embeddings – AllMiniLmL6V2 量化模型（384 维）
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2-q:1.0.0-beta1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
