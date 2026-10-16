description = "Chronos Persistence Adapter: Virtual Thread Async Snapshot Serialization"

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreApplication)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation(libs.kryo.core)
}
