plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    maven {
        url = uri("https://maven.pkg.github.com/huanshankeji/gradle-common")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    implementation(kotlin("gradle-plugin", "1.8.21"))
    implementation("com.huanshankeji:common-gradle-dependencies:0.6.0-20230609")
    implementation("com.huanshankeji.team:gradle-plugins:0.5.0-SNAPSHOT") // TODO: don't use a snapshot version in a main branch
}
