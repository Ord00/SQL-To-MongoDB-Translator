plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":Model"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(project(":Scanner"))
}