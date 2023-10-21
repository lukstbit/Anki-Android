plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle-api:8.1.2")
}

gradlePlugin {
    plugins {
        create("JacocoConfigPlugin") {
            id = "com.ichi2.anki.plugins.jacoco"
            implementationClass = "com.ichi2.anki.build.plugins.JacocoConfigPlugin"
        }
    }
}