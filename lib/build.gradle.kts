plugins {
    conventions
    id("com.huanshankeji.team.dokka.github-dokka-convention")
}

dependencies {
    implementation(commonDependencies.exposed.core())
    //implementation(commonDependencies.kotlinCommon.exposed())
    implementation(commonDependencies.kotlinCommon.reflect())
    implementation(commonDependencies.kotlinCommon.core())
}
