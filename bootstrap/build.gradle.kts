plugins {
    application
}

description = "Chronos Bootstrap Module: Composition Root, Virtual Thread Wiring, and Main Entrypoint"

application {
    mainClass.set("io.chronos.engine.bootstrap.Main")
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
        "-Xms256m",
        "-Xmx1024m"
    )
}

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreApplication)
    implementation(projects.adapterPersistence)
    implementation(projects.adapterNetwork)
    implementation(projects.adapterPresentation)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    implementation(libs.micrometer.core)
}
