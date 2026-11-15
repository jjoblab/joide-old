plugins {
    `java-library`
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.9")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
