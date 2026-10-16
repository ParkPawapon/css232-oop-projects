## Summary

Briefly describe the architectural scope and purpose of this Pull Request.

## Architectural & Quality Gate Checklist

- [ ] **Branch & Commit Naming:** Uses standard type prefixes (`feat/`, `build/`, `ci/`, `test/`, `docs/`, `refactor/`, `chore/`) and Conventional Commits (no `ai/` or `codex/` prefixes).
- [ ] **Clean / Hexagonal Architecture:** Dependency direction points strictly inward (`:bootstrap` -> `:adapter-*` -> `:core-application` -> `:core-domain`).
- [ ] **Domain Purity:** `:core-domain` introduces zero external runtime framework dependencies (`verifyZeroRuntimeDependencies` + ArchUnit).
- [ ] **Static Analysis:** Passes Spotless, Checkstyle, PMD, and SpotBugs with zero warnings/errors (`-Xlint:all -Werror`).
- [ ] **Test Coverage:** Passes JUnit 5, AssertJ, and JaCoCo verification ($\ge 85\%$ instruction and branch coverage).
