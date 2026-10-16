# Chronos Enterprise Base Game System Architecture (Java 21 LTS)

Production-grade, game-agnostic **Base Game System Architecture** written in **Java 21 LTS**, hybridizing **Hexagonal / Clean Architecture (Ports & Adapters)** for macro-level decoupling with **Data-Oriented Design / Entity-Component-System (ECS)** for micro-level, cache-friendly deterministic simulation ticks.

---

## Architectural Highlights

- **Strict Multi-Module Dependency Rule:**
  `:bootstrap` $\rightarrow$ (`:adapter-persistence`, `:adapter-network`, `:adapter-presentation`) $\rightarrow$ `:core-application` $\rightarrow$ `:core-domain`
- **Zero-Dependency Core Domain (`:core-domain`):**
  Pure Java 21 SE (`record` components/events, `sealed` state machine, 64-bit packed `EntityId`, bitmask-filtered `World` ECS registry). Verified at build time via `verifyZeroRuntimeDependencies` and **ArchUnit**.
- **Deterministic Fixed-Timestep Loop (`:core-application`):**
  60 UPS simulation accumulator with spiral-of-death clamping, render interpolation factor $\alpha \in [0.0, 1.0]$, lock-free power-of-two ring buffer event bus, and thread-safe CQRS `CommandBus`.
- **Java 21 Virtual Threads (`:adapter-*` & `:bootstrap`):**
  Non-blocking snapshot persistence (`VirtualThreadSnapshotAdapter`), state packet broadcasting (`VirtualThreadNetworkGateway`), Micrometer telemetry (`MicrometerTelemetryAdapter`), and zero-data-loss JVM shutdown hooks.

---

## Quality Gates & Governance

| Gate | Tooling | Threshold / Rule |
| :--- | :--- | :--- |
| **Compiler** | OpenJDK 21 LTS | `-Xlint:all -Xlint:-serial -Werror -parameters` |
| **Formatting** | Spotless | Google Java Format (`1.24.0`) |
| **Style & Lint** | Checkstyle `10.20.1` | `maxWarnings = 0` |
| **Static Analysis** | PMD `7.7.0` & SpotBugs `4.8.6` | `Effort.MAX`, `Confidence.LOW`, `ignoreFailures = false` |
| **Architecture** | ArchUnit `1.3.0` | Enforces layered boundaries, record immutability, and domain purity |
| **Test Coverage** | JUnit 5 + AssertJ + JaCoCo `0.8.12` | $\ge 85\%$ Instruction and Branch coverage per module |
| **Branch Policy** | GitHub Rulesets + CI | Typed branches (`feat/`, `build/`, `ci/`, `test/`, `docs/`), $\ge 1$ review (`@ParkPawapon`), sole admin bypass (`@ParkPawapon`) |

---

## Quick Start

```bash
# Run formatting check, static analysis, unit/ArchUnit tests, and JaCoCo >=85% verification
./gradlew spotlessCheck check

# Execute the headless verification tick & graceful shutdown
./gradlew :bootstrap:run
```
