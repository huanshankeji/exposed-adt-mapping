plugins {
    conventions
}

dependencies {
    implementation(commonDependencies.exposed.core())
    //implementation(commonDependencies.kotlinCommon.exposed())
    implementation(commonDependencies.kotlinCommon.reflect())
}
