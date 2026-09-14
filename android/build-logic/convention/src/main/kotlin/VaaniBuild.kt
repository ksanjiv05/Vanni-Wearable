import org.gradle.api.JavaVersion

/** Central toolchain constants shared by every convention plugin. */
object VaaniBuild {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 29
    const val TARGET_SDK = 36
    const val BUILD_TOOLS = "36.0.0"
    val JAVA = JavaVersion.VERSION_17
    const val JVM_TARGET = "17"
}
