plugins {
    id("org.springframework.boot")
}

val testcontainersVersion = "1.21.4"

dependencies {
    implementation(project(":Model"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")

    // TestContainers
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:rabbitmq:1.21.4")
    testImplementation("org.springframework.amqp:spring-rabbit-test")

    // Docker Java client dependencies (DockerComposeContainer)
    testImplementation("com.github.docker-java:docker-java-api:3.7.1")
    testImplementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
    testImplementation("com.github.docker-java:docker-java-core:3.7.1")
}