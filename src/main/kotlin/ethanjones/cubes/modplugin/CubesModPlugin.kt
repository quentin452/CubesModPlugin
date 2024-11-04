package ethanjones.cubes.modplugin

import com.android.dx.command.dexer.Main
import org.gradle.api.*
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import java.io.PrintWriter
import java.util.Properties
import org.gradle.api.tasks.bundling.Jar

class CubesModPlugin : Plugin<Project> {
    
    companion object {
        const val CLIENT_CLASS = "ethanjones.cubes.core.platform.desktop.ClientLauncher"
        const val SERVER_CLASS = "ethanjones.cubes.core.platform.desktop.ServerLauncher"
    }

    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)
        val cubesExtension = project.extensions.create("cubes", CubesModPluginExtension::class.java)
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        val runClientSourceSet = sourceSets.create("cubesRunClient")
        val runClientCompileConfiguration = project.configurations.getByName(runClientSourceSet.compileClasspathConfigurationName)

        val runServerSourceSet = sourceSets.create("cubesRunServer")
        val runServerCompileConfiguration = project.configurations.getByName(runServerSourceSet.compileClasspathConfigurationName)

        
        project.extensions.create("cubes", CubesModPluginExtension::class.java)

        project.tasks.register("modDex", Task::class.java) {
            it.dependsOn(project.tasks.getByName("jar"))
            it.description = "Creates .dex to run on android"
            it.group = "cubes"
            it.doLast {
                File(project.layout.buildDirectory.get().asFile , "/libs/").mkdirs()

                val arguments = Main.Arguments()
                arguments.parse(arrayOf("--output=${project.layout.buildDirectory.get().asFile }/libs/mod.dex", "${project.layout.buildDirectory.get().asFile }/libs/mod.jar"))
                val result = Main.run(arguments)
                if (result != 0) throw GradleException("Failed to convert jar to dex [$result]")
            }
        }

        project.tasks.register("modProperties", {
            it.description = "Creates mod properties file"
            it.group = "cubes"
            it.doLast {
                File(project.layout.buildDirectory.get().asFile , "/libs/").mkdirs()
                val props = Properties()
                props.setProperty("modClass", cubesExtension.modClass)
                props.setProperty("modName", cubesExtension.modName)
                props.setProperty("modVersion", cubesExtension.modVersion)
                PrintWriter(File(project.layout.buildDirectory.get().asFile , "/libs/mod.properties")).use { printWriter ->
                    props.store(printWriter, null)
                }
            }
        })

        project.tasks.register("cm", Zip::class.java) {
            it.description = "Builds Cubes cm file"
            it.group = "cubes"
            it.destinationDirectory.set(File(project.layout.buildDirectory.get().asFile , "/libs/"))
            if (cubesExtension.buildDesktop) {
                it.from(File(project.layout.buildDirectory.get().asFile , "/libs/mod.jar"))
            }
            if (cubesExtension.buildAndroid) {
                it.from(File(project.layout.buildDirectory.get().asFile , "/libs/mod.dex"))
            }
            it.from(File(project.layout.buildDirectory.get().asFile , "/libs/mod.properties"))
            it.into("assets") {
                it.from(File(cubesExtension.assetsFolder))
            }
            it.into("json") {
                it.from(File(cubesExtension.jsonFolder))
            }
            it.outputs.upToDateWhen { false }
        }

        project.tasks.register("runClient") {
            it.description = "Runs Cubes Client"
            it.group = "cubes"
            it.dependsOn(project.tasks.getByName("cm"))
            it.doLast {
                File(project.layout.buildDirectory.get().asFile .absolutePath + "/run/client").mkdirs()
                project.javaexec {
                    it.args = listOf("--mod", project.tasks.named("cm", Zip::class.java).get().archiveFile.get().asFile.absolutePath) + (cubesExtension.runClientArguments)
                    it.classpath = project.files(runClientCompileConfiguration.files)
                    it.mainClass.set(CLIENT_CLASS)
                    it.maxHeapSize = cubesExtension.runClientHeapSize
                    it.workingDir = File(project.layout.buildDirectory.get().asFile .absolutePath + "/run/client")
                }
            }
        }
        project.tasks.register("runServer") {
            it.description = "Runs Cubes Server"
            it.group = "cubes"
            it.dependsOn(project.tasks.getByName("cm"))
            it.doLast {
                File(project.layout.buildDirectory.get().asFile .absolutePath + "/run/server").mkdirs()
                project.javaexec {
                    it.args = listOf("--mod", project.tasks.named("cm", Zip::class.java).get().archiveFile.get().asFile.absolutePath) + cubesExtension.runServerArguments
                    it.classpath = project.files(runServerCompileConfiguration.files)
                    it.mainClass.set(SERVER_CLASS)
                    it.maxHeapSize = cubesExtension.runServerHeapSize
                    it.workingDir = File(project.layout.buildDirectory.get().asFile .absolutePath + "/run/server")
                }
            }
        }

        addMavenCentral(project)
        //ddMavenRepo(project, "https://oss.sonatype.org/content/repositories/snapshots/")
        //addMavenRepo(project, "https://oss.sonatype.org/content/repositories/releases/")
        //addMavenRepo(project, "http://ethanjones.me/maven/snapshots")
        //addMavenRepo(project, "http://ethanjones.me/maven/releases")

        project.afterEvaluate {
            val version = cubesExtension.cubesVersion

            val dep = project.dependencies.add("compile", "ethanjones.cubes:core:$version")
            project.configurations.getByName("compile").dependencies.add(dep)

            val clientDep = project.dependencies.add(runClientCompileConfiguration.name, "ethanjones.cubes:client:$version")
            runClientCompileConfiguration.dependencies.add(clientDep)

            val serverDep = project.dependencies.add(runServerCompileConfiguration.name, "ethanjones.cubes:server:$version")
            runServerCompileConfiguration.dependencies.add(serverDep)

            val cm = project.tasks.named("cm", Zip::class.java).get()
            cm.archiveFileName.set("${cubesExtension.modName}.cm")

            cm.dependsOn(project.tasks.getByName("modProperties"))
            if (cubesExtension.buildAndroid) cm.dependsOn(project.tasks.getByName("modDex"))
            if (cubesExtension.buildDesktop) cm.dependsOn(project.tasks.getByName("jar"))
        }

        project.tasks.withType(JavaCompile::class.java).configureEach {
            it.sourceCompatibility = "17"
            it.targetCompatibility = "17"
        }

        project.tasks.named("jar", Jar::class.java) {
            it.archiveFileName.set("mod.jar")
        }
    }

    private fun addMavenRepo(project: Project, url: String) {
        project.repositories.maven {
            it.url = project.uri(url)
        }
    }

    private fun addMavenCentral(project: Project) {
        project.repositories.mavenCentral()
    }
}

open class CubesModPluginExtension {
    var cubesVersion: String = ""
    var modVersion: String = ""
    var modClass: String = ""
    var modName: String = ""
    var assetsFolder: String = "assets/"
    var jsonFolder: String = "json/"
    var runClientHeapSize: String = "2G"
    var runClientArguments: List<String> = emptyList()
    var runServerHeapSize: String = "2G"
    var runServerArguments: List<String> = emptyList()
    var buildAndroid: Boolean = false
    var buildDesktop: Boolean = true
} 