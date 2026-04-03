plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":Model"))
    implementation(project(":Scanner"))
    implementation(project(":Parser"))
    implementation(project(":IRGenerator"))
    implementation(project(":CodeGenerator"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}