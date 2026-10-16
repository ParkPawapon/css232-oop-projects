description = "Chronos Core Domain: Pure Java 21 ECS, State Machine, Events, and Hexagonal Ports"

dependencies {
    // Strictly ZERO runtime/implementation dependencies.
}

tasks.register("verifyZeroRuntimeDependencies") {
    description = "Fails the build if :core-domain declares any external implementation or runtime dependency."
    doLast {
        val forbiddenConfigs = listOf("api", "implementation", "compileOnly", "runtimeOnly")
        forbiddenConfigs.forEach { cfgName ->
            val deps = configurations.getByName(cfgName).dependencies
            check(deps.isEmpty()) {
                "Architectural violation: :core-domain must remain 100% dependency-free, " +
                    "but found dependencies in '$cfgName': ${deps.map { "${it.group}:${it.name}" }}"
            }
        }
    }
}

tasks.named("check") {
    dependsOn("verifyZeroRuntimeDependencies")
}
