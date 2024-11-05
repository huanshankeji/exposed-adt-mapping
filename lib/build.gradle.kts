plugins {
    conventions
    id("org.jetbrains.dokka")
}

dependencies {
    implementation(commonDependencies.exposed.core())
    //implementation(commonDependencies.kotlinCommon.exposed())
    implementation(commonDependencies.kotlinCommon.reflect())
    implementation(commonDependencies.kotlinCommon.core())
}

dokka {
    dokkaSourceSets.all {
        sourceLink {
            remoteUrl("https://github.com/huanshankeji/exposed-adt-mapping/tree/65548c38a72f037345ad0aff4982522348753fdf/lib")
            remoteLineSuffix.set("#L")
        }
    }
}
