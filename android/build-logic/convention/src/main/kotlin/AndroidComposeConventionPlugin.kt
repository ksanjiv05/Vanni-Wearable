import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // Works for both application and library modules. The android extension
        // is registered under its concrete subtype, so look it up by name.
        val commonExtension = extensions.getByName("android") as CommonExtension<*, *, *, *, *, *>
        commonExtension.buildFeatures.compose = true

        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            fun impl(alias: String) =
                add("implementation", libs.findLibrary(alias).get())

            impl("androidx-compose-ui")
            impl("androidx-compose-ui-graphics")
            impl("androidx-compose-ui-tooling-preview")
            impl("androidx-compose-material3")
            impl("androidx-compose-material-icons-extended")

            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
