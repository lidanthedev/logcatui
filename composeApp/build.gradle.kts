import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val releaseVersion = providers.gradleProperty("releaseVersion").orElse("1.0.0")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.adblib)
            implementation(libs.jewel.int.ui.standalone)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.slf4j.simple)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.junit)
        }
    }
}


compose.desktop {
    application {
        mainClass = "me.lidan.logcatui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Exe)
            packageName = "LogcatUI"
            packageVersion = releaseVersion.get()

            windows {
                menu = true
                menuGroup = "LidanDev"
                shortcut = true
            }
            linux {
                shortcut = true
                menuGroup = "Development"
            }
        }
    }
}
