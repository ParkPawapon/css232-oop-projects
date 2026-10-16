description = "Chronos Network Adapter: Virtual Thread Non-Blocking Transport Gateway"

dependencies {
    implementation(projects.coreDomain)
    implementation(projects.coreApplication)
    implementation(libs.slf4j.api)
    implementation(libs.netty.transport)
}
