plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":Model"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.amqp:spring-rabbit:3.0.13")

    implementation(project(":Scanner"))
    implementation(project(":Parser"))
    implementation(project(":IRGenerator"))
    implementation(project(":CodeGenerator"))
}