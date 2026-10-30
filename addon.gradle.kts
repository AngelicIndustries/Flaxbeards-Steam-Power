import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.language.jvm.tasks.ProcessResources

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runServer")) {
        doFirst("prepareWawelAuthDevConfig") {
            // Fresh checkouts and CI need a local endpoint; never replace an existing config.
            val configFile = workingDir.resolve("config/wawelauth/server.json")
            if (!configFile.exists()) {
                configFile.parentFile.mkdirs()
                configFile.writeText("""
                    {
                      "publicBaseUrl": "http://localhost:25565/",
                      "enableLocalAuth": false,
                      "enablePublicPage": false,
                      "enablePublicInfoApi": false
                    }
                """.trimIndent() + "\n")
            }
        }
        doFirst("stripClientOnlyMods") {
            val clientOnlyModIds = listOf("angelica", "ModularUI2", "catalogue")
            classpath = classpath.filter { file ->
                clientOnlyModIds.none { file.name.contains(it, ignoreCase = true) }
            }
        }
    }
}

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Flaxbeard's Steam Power")
            description.set("Maintained fork of Flaxbeard's Steam Power for Minecraft 1.7.10.")
            url.set("https://github.com/AngelicIndustries/Flaxbeards-Steam-Power")
            organization {
                name.set("Angelic Industries")
                url.set("https://angelic.industries/")
            }
            licenses {
                license {
                    name.set("GNU Lesser General Public License, version 3")
                    url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                    distribution.set("repo")
                }
            }
            scm {
                url.set("https://github.com/AngelicIndustries/Flaxbeards-Steam-Power")
                connection.set("scm:git:https://github.com/AngelicIndustries/Flaxbeards-Steam-Power.git")
                developerConnection.set("scm:git:ssh://git@github.com/AngelicIndustries/Flaxbeards-Steam-Power.git")
            }
        }
    }
}

// Keep the upstream notices alongside the mod license in published jars.
tasks.named<ProcessResources>("processResources") {
    from("LICENSE")
    from("licenses") {
        into("assets/steamcraft/licenses")
    }
}
