import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

fun exec(working: String, cmd: String) {
    ProcessBuilder("CMD", "/C", cmd).apply {
        inheritIO()
        directory(File(working))
        start().apply {
            waitFor()
            destroy()
        }
    }
}

fun main(args: Array<String>) {
    val appName = args[0].replace(" ", "")
    val pkgName = args[1]
    val platVer = args[2]

    val buildTools = "C:/Users/Rafael/AppData/Local/Android/Sdk/build-tools/28.0.3"
    val platform = "C:/Users/Rafael/AppData/Local/Android/Sdk/platforms/android-$platVer"

    val genMaven = "mvn archetype:generate -DgroupId=$pkgName -DartifactId=$appName -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.4 -DinteractiveMode=false"
    val working = System.getProperty("user.dir")

    //val path = Paths.get("$working\\android-project-template")
    exec(working, "git clone https://github.com/orafl/android-project-template.git")
    exec(working, "ren android-project-template $appName")

    val inQuotes = Regex("([\"'])(?:(?=(\\\\?))\\2.)*?\\1")

    fun String.bind(vararg map: Pair<String, String>): String {
        map.forEach { (k, v) ->
            if (startsWith(k)) return inQuotes.replace(this, v)
        }
        return ""
    }

    val buildScript = "$working\\$appName\\build.sh"

    Files.lines(Paths.get(buildScript)).use { lines ->
        var iInit = -1
        var iRun = -1
        val newLines = lines.map {
            val line = it.trim()
            var result = line.bind(
                "APP_NAME" to appName,
                "PACKAGE_NAME" to pkgName,
                "AAPT" to "$buildTools/aapt",
                "DX" to "$buildTools/dx",
                "ZIPALIGN" to "$buildTools/zipalign",
                "APKSIGNER" to "$buildTools/apksigner",
                "PLATFORM" to "$platform/android.jar"
            ).takeIf { s -> s.isNotEmpty() } ?: line

            if (line.startsWith('}')) {
                iInit = -1; iRun = -1
                result = line
            }
            if(iInit > -1) {
                result = when(iInit) {
                    2 -> genMaven
                    8 -> "mv ${'$'}{APP_NAME}/* ."
                    9 -> "sed \"s/{{ PACKAGE_NAME }}/${'$'}{PACKAGE_NAME}/\" \"template_files/MainActivity.java\" > src/main/java/${pkgName.replace('.', '/')}/MainActivity.java"
                    else -> line
                }
                iInit++
            }
            if(iRun > -1) {
                val adb = "adb install -r \"target/classes/${'$'}{APP_NAME}.apk\""
                result = if (iRun == 1) adb else line
                iRun++
            }

            if (line.startsWith("init()")) {
                iInit = 0; result = line
            }
            if (line.startsWith("run()")) {
                iRun = 0; result = line
            }

            result
        }.toList()

        PrintWriter(FileOutputStream(File(buildScript), false)).use { p ->
            newLines.forEach { l ->
                p.println(l)
            }
        }
    }
}