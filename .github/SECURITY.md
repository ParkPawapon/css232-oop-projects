# Security Policy

## Supported Versions

| Version | Supported          | Java Runtime |
| ------- | ------------------ | ------------ |
| 1.0.x   | :white_check_mark: | Java 21 LTS  |

## Enterprise Security & Architectural Controls

1. **Branch Protection & Rulesets:** Direct pushes, force-pushes, and branch deletions on `main` are blocked.
2. **Mandatory Code Review:** Every Pull Request requires at least **1 approving review** from `@ParkPawapon` (`CODEOWNERS`).
3. **Exclusive Bypass Authority:** Only `@ParkPawapon` (Repository Administrator) holds bypass privileges for emergency or administrative releases.
4. **Automated Quality & Security Gates:**
   - Compiler hardening: `-Xlint:all -Werror` on Java 21 LTS.
   - Domain isolation: `:core-domain` enforces zero external runtime dependencies (`verifyZeroRuntimeDependencies` + ArchUnit).
   - Static analysis: Spotless (Google Java Format), Checkstyle, PMD 7, and SpotBugs (`Effort.MAX`).
   - Test coverage: JaCoCo enforces $\ge 85\%$ instruction and branch coverage on every module.

## Reporting a Vulnerability

Please report suspected security vulnerabilities privately via GitHub Security Advisories to `@ParkPawapon`.
