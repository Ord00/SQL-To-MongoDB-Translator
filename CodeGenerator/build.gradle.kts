plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":Model"))
    implementation("org.springframework.amqp:spring-rabbit:3.0.13")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(project(":Scanner"))
    testImplementation(project(":Parser"))
    testImplementation(project(":IRGenerator"))
}