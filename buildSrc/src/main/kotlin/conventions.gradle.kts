import com.huanshankeji.team.`Shreck Ye`
import com.huanshankeji.team.pomForTeamDefaultOpenSource
import com.huanshankeji.team.repositoriesAddTeamGithubPackagesMavenRegistry

plugins {
    id("com.huanshankeji.kotlin-jvm-library-sonatype-ossrh-publish-conventions")
    id("com.huanshankeji.team.with-group")
    id("com.huanshankeji.team.default-github-packages-maven-publish")
}

repositories {
    mavenLocal()
    mavenCentral()
}
repositoriesAddTeamGithubPackagesMavenRegistry("kotlin-common")

kotlin.jvmToolchain(8)

version = projectVersion

publishing.publications.withType<MavenPublication> {
    pomForTeamDefaultOpenSource(
        project,
        "Exposed ADT mapping",
        "mappings between data entities and tables with support for (generic) algebraic data types based on Exposed DSL"
    ) {
        `Shreck Ye`()
    }
}
