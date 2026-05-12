plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.1.0"
    jacoco
}

group = "app.platformcore"
version = "0.0.1-SNAPSHOT"
description = "PlatformCore"

val emailSenderDir = file("../email-sender")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    create("mockitoAgent") {
        isTransitive = false
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.nimbusds:nimbus-jose-jwt:10.7")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:redpanda:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    add("mockitoAgent", "org.mockito:mockito-core")
}

sourceSets {
    named("test") {
        java.exclude("app/platformcore/apiusers/e2e/**")
    }

    create("crossServiceE2e") {
        java.srcDir("src/crossServiceE2e/java")
        resources.srcDir("src/crossServiceE2e/resources")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

configurations.named("crossServiceE2eImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("crossServiceE2eRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    if (emailSenderDir.isDirectory) {
        add("crossServiceE2eImplementation", "app.platformcore:email-sender:0.0.1-SNAPSHOT")
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgumentProviders += CommandLineArgumentProvider {
        val mockitoAgent = configurations.getByName("mockitoAgent").singleFile
        listOf("-javaagent:${mockitoAgent.absolutePath}")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register<Test>("crossServiceE2eTest") {
    description = "Runs minimal cross-service E2E tests between api-users and email-sender."
    group = "verification"
    doFirst {
        check(emailSenderDir.isDirectory) {
            "crossServiceE2eTest requires ../email-sender. Run it from the full backend repository checkout."
        }
    }
    testClassesDirs = sourceSets["crossServiceE2e"].output.classesDirs
    classpath = sourceSets["crossServiceE2e"].runtimeClasspath
    shouldRunAfter(tasks.test)
    failOnNoDiscoveredTests = true
    useJUnitPlatform()
    jvmArgumentProviders += CommandLineArgumentProvider {
        val mockitoAgent = configurations.getByName("mockitoAgent").singleFile
        listOf("-javaagent:${mockitoAgent.absolutePath}")
    }
}

val jacocoExcludes = listOf(
    "**/*Application*",
    "**/dto/**",
    "**/model/**",
    "**/entity/**",
    "**/common/**",
    "**/advice/**",
    "**/exception/**",
    "**/config/**",
    "**/mapper/**",
    "**/domain/**"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    finalizedBy(tasks.jacocoTestCoverageVerification)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.asFileTree.matching {
                exclude(jacocoExcludes)
            }
        )
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = 0.7.toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.asFileTree.matching {
                exclude(jacocoExcludes)
            }
        )
    )
}
