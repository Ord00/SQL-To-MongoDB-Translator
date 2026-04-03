plugins {
	java
	id("org.springframework.boot") version "3.5.3" apply false
	id("io.spring.dependency-management") version "1.1.7"
	id("org.springdoc.openapi-gradle-plugin") version "1.8.0" apply false
    id("com.github.node-gradle.node") version "7.0.2"
}

group = "sql.to.mongodb.translator"
version = "0.0.1-SNAPSHOT"

allprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group = "sql.to.mongodb.translator"
    version = "0.0.1-SNAPSHOT"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(22))
        }
    }

    repositories {
        mavenCentral()
    }

    configurations {
        compileOnly {
            extendsFrom(configurations.annotationProcessor.get())
        }
    }
}

subprojects {
    apply(plugin = "org.springdoc.openapi-gradle-plugin")

    dependencies {
        annotationProcessor("org.projectlombok:lombok:1.18.30")
        compileOnly("org.projectlombok:lombok:1.18.30")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")

        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.3"))
        testImplementation("org.testcontainers:testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")
        testImplementation("org.testcontainers:postgresql")
        testImplementation("org.testcontainers:kafka")

        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")

        testImplementation("org.mockito:mockito-core:5.4.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.4.0")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
    }
}
