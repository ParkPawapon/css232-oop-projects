import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import java.math.BigDecimal

plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
}

allprojects {
    group = "io.chronos.engine"
    version = "1.0.0-RELEASE"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)
    apply(plugin = rootProject.libs.plugins.spotbugs.get().pluginId)

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(rootProject.libs.versions.jdk.get().toInt()))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:all",
                "-Xlint:-serial",
                "-Werror",
                "-parameters"
            )
        )
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("-XX:+UseZGC", "-XX:+ZGenerational")
        finalizedBy(tasks.named("jacocoTestReport"), tasks.named("jacocoTestCoverageVerification"))
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = rootProject.libs.versions.jacocoTool.get()
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("jacocoTestReport"))
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.85")
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.85")
                }
            }
        }
    }

    extensions.configure<CheckstyleExtension> {
        toolVersion = rootProject.libs.versions.checkstyleTool.get()
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
        maxWarnings = 0
    }

    extensions.configure<PmdExtension> {
        toolVersion = rootProject.libs.versions.pmdTool.get()
        isConsoleOutput = true
        isIgnoreFailures = false
        ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
        ruleSets = emptyList()
    }

    extensions.configure<SpotBugsExtension> {
        toolVersion.set(rootProject.libs.versions.spotbugsEngine.get())
        effort.set(Effort.MAX)
        reportLevel.set(Confidence.LOW)
        excludeFilter.set(rootProject.file("config/spotbugs/excludes.xml"))
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required.set(true) }
        reports.create("xml") { required.set(false) }
    }

    extensions.configure<SpotlessExtension> {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        java {
            googleJavaFormat("1.24.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    dependencies {
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.bundles.testing.suite)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
    }
}
