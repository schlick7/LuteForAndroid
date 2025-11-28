import java.io.File
import java.util.regex.Pattern

fun incrementVersionInBuildGradle() {
    val buildFile = File(project.projectDir, "build.gradle.kts")
    val stringsFile = File(project.projectDir, "src/main/res/values/strings.xml")

    if (buildFile.exists()) {
        val content = buildFile.readText()
        // Pattern to match versionCode and versionName in build.gradle.kts
        val versionCodePattern = Pattern.compile("versionCode\\s*=\\s*(\\d+)")
        val versionNamePattern = Pattern.compile("versionName\\s*=\\s*\"(\\d+)\\.(\\d+)\\.(\\d+)\"")

        val versionCodeMatcher = versionCodePattern.matcher(content)
        val versionNameMatcher = versionNamePattern.matcher(content)

        if (versionCodeMatcher.find() && versionNameMatcher.find()) {
            val versionCode = versionCodeMatcher.group(1).toInt()
            val major = versionNameMatcher.group(1).toInt()
            val minor = versionNameMatcher.group(2).toInt()
            val patch = versionNameMatcher.group(3).toInt()

            val newVersionCode = versionCode + 1
            val newPatch = patch + 1
            val newVersionName = "$major.$minor.$newPatch"

            // Replace versionCode and versionName in build.gradle.kts
            val newContent =
                    content.replace("versionCode = $versionCode", "versionCode = $newVersionCode")
                            .replace(
                                    "versionName = \"$major.$minor.$patch\"",
                                    "versionName = \"$major.$minor.$newPatch\""
                            )

            buildFile.writeText(newContent)
            println("Incremented version from $major.$minor.$patch to $major.$minor.$newPatch")
            println("Updated versionCode from $versionCode to $newVersionCode")

            // Also update the version in strings.xml
            if (stringsFile.exists()) {
                val stringsContent = stringsFile.readText()
                val newStringsContent =
                        stringsContent.replace(
                                "<string name=\"app_version\">App Version $major.$minor.$patch</string>",
                                "<string name=\"app_version\">App Version $major.$minor.$newPatch</string>"
                        )
                stringsFile.writeText(newStringsContent)
                println("Updated version in strings.xml to App Version $major.$minor.$newPatch")
            }
        } else {
            println("Could not find version information in build.gradle.kts")
        }
    } else {
        println("build.gradle.kts file not found")
    }
}

tasks.register("incrementVersion") { doLast { incrementVersionInBuildGradle() } }

// Increment version only for release builds
tasks.whenTaskAdded {
    if (this.name == "preReleaseBuild") {
        this.dependsOn("incrementVersion")
    }
}
