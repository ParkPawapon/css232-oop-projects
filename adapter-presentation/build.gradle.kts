description = "Chronos Presentation & Telemetry Adapter: Headless Renderer and Micrometer Metrics"

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreApplication)
    implementation(libs.slf4j.api)
    implementation(libs.micrometer.core)
}
