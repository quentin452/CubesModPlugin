import java.util.Properties
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("maven-publish")
}

group = "ethanjones.cubes"
version = "0.0.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val isRelease = true
val dxVersion = "7.1.2_r33"

val localProperties = Properties().apply {
    if (file("build.properties").exists()) {
        file("build.properties").reader().use { load(it) }
    }
}

repositories {
    mavenCentral()
    maven(url = "https://ethanjones.me/maven/snapshots/")
    maven(url = "https://ethanjones.me/maven/releases/")
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("ethanjones.repackaged.android:dx:$dxVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

fun executeCommand(command: String): String? {
    return try {
        val process = ProcessBuilder(*command.split(" ").toTypedArray())
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText().trim() }
    } catch (e: Exception) {
        null
    }
}

val gitHubUrl = executeCommand("git config --get remote.origin.url")
val gitHubUsername = gitHubUrl?.split(":")?.lastOrNull()?.split("/")?.firstOrNull() ?: "NO_USERNAME_FOUND_ERROR"
val repositoryName = gitHubUrl?.split("/")?.lastOrNull()?.removeSuffix(".git") ?: "NO_REPOSITORY_FOUND_ERROR"

val publishToMaven = project.findProperty("PUBLISH_TO_MAVEN")?.toString() ?: "false"

publishing {
    publications {
        create<MavenPublication>("jar") {
            artifact(tasks["jar"])
            groupId = "ethanjones.cubes"
            artifactId = "modplugin"
            version = if (!isRelease) "$version-SNAPSHOT" else version

            /*pom {
                withXml {
                    asNode().apply {
                        appendNode("repositories").apply {
                            appendNode("repository").apply {
                                appendNode("id", "ethanjonesSnapshots")
                                appendNode("url", "http://ethanjones.me/maven/snapshots/")
                            }
                            appendNode("repository").apply {
                                appendNode("id", "ethanjonesReleases")
                                appendNode("url", "http://ethanjones.me/maven/releases/")
                            }
                        }
                        appendNode("dependencies").apply {
                            appendNode("dependency").apply {
                                appendNode("groupId", "ethanjones.repackaged.android")
                                appendNode("artifactId", "dx")
                                appendNode("version", dxVersion)
                            }
                        }
                    }
                }
            }*/
        }
    }
    if (publishToMaven.toBoolean()) {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$gitHubUsername/$repositoryName")
                credentials {
                    username = project.findProperty("GITHUB_USER")?.toString() ?: System.getenv("USERNAME")
                    password = project.findProperty("GITHUB_USER_TOKEN")?.toString() ?: System.getenv("TOKEN")
                }
            }
        }
    } else {
        println("Publishing to Maven is disabled. Skipping Maven publishing.")
    }
}

fun getMavenVersionString(): String {
    return if (!isRelease) "$version-SNAPSHOT" else version.toString()
}
fun getMavenRepo(): String {
    return if (isRelease) "${localProperties.getProperty("MAVEN_REPO_PATH")}releases" else "${localProperties.getProperty("MAVEN_REPO_PATH")}snapshots"
}
