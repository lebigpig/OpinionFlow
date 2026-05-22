plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.springframework.boot") version "3.4.5" apply false
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    kotlin("plugin.jpa") version "2.1.20" apply false
    kotlin("plugin.noarg") version "2.1.20" apply false
}

extra["springCloudVersion"] = "2024.0.1"
extra["springCloudAlibabaVersion"] = "2023.0.3.2"

subprojects {
    group = "com.lespider.opinionflow"
    version = "0.0.1-SNAPSHOT"

    apply(plugin = "io.spring.dependency-management")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
            mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:${property("springCloudAlibabaVersion")}")
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
        jvmArgs("-Dspring.cloud.compatibility-verifier.enabled=false")
    }
}
