import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Shared android{} block applied by both application and library convention plugins. */
internal fun Project.configureAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = VaaniBuild.COMPILE_SDK
        buildToolsVersion = VaaniBuild.BUILD_TOOLS

        defaultConfig {
            minSdk = VaaniBuild.MIN_SDK
        }

        compileOptions {
            sourceCompatibility = VaaniBuild.JAVA
            targetCompatibility = VaaniBuild.JAVA
        }

        lint {
            // Fail the build on lint errors; warnings stay advisory for now.
            abortOnError = true
            warningsAsErrors = false
            checkDependencies = true
            // Baseline lets us gate on new issues without blocking on the
            // existing (non-critical) warning backlog.
            baseline = file("lint-baseline.xml")
        }
    }

    extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
