## Project Architecture

**Demiurge**: Java 26 multi-module Maven project (JPMS). Root POM aggregates
4 modules with shared JUnit 5 + JaCoCo config.

```
demiurge/                 -- Root POM (pom packaging, Java 26)
├── action/               -- Dev workflow scripts (test, produce, regenerate)
├── addition/             -- Utility scripts, configs, and code review reports
├── flora-project/        -- Core Java library suite (pom packaging)
│   ├── flora-root/       -- Zero-dependency utility library
│   ├── flora-ramet/      -- Template-based code generation engine
│   ├── flora-garden/     -- Placeholder module
│   ├── flora-tangle/     -- Java bytecode obfuscator
│   ├── flora/            -- Aggregate (root + garden)
│   └── flora-benchmark/  -- JMH microbenchmarks
├── chainlink/            -- Placeholder module
├── pleroma/              -- Placeholder module
└── playground/           -- Dev sandbox entry point
```

### Self-Bootstrapping (Meta-Code Generation)

Template files in `flora-root/src/{main,test}/templates/*.ftl` are rendered by
the `regenerate` Maven profile (`exec-maven-plugin` → `com.flora.codegen.Ramet`)
to produce the 64 `*FastHashMap` classes. The profile only activates during
`generate-sources` / `generate-test-sources` phase.

## Build & Test Commands

- `./action/test.cmd` — Run all tests
- `./action/produce.cmd` — Full build (`mvnw clean install -DskipTests`)
- `./action/regenerate.cmd` — Regenerate code from templates
  (`mvnw generate-test-sources -Pregenerate`)
-
- `./mvnw test` — Run all tests
- `./mvnw test -Dtest=TokenTest` — Run a single test class
- `./mvnw test -Dtest=TokenTest#testVarToken` — Run a single test method
- `./mvnw clean install -DskipTests` — Full build, skip tests
-
- `./push.cmd "commit message"` — Push to all remotes in
  `addition/config/remoteRepoList.txt`

## Module Documentation

- [flora-root结构概述.md](flora-project/flora-root/flora-root结构概述.md)
- [flora-ramet结构概述.md](flora-project/flora-ramet/flora-ramet结构概述.md)
- [代码生成模板语法简介.md](flora-project/flora-ramet/代码生成模板语法简介.md)

## Code Review

Store code review reports in `addition/codereview/`. Naming:
`review{YYYYMMDD}-{NN}.md`. All AI-generated reviews go here.

## AI Guidelines

- **Language**: Write this file in English.
- **Line width**: Wrap lines in this file at 90 characters.
- **Module content**: Write module-level docs as separate `.md` files in the
  module directory, then link them in the Module Documentation section above.
- **Protected directories**: NEVER modify any files under
  `addition/otherproject/` — those are third-party projects for reference only.
- **Commit & push after each task**: After completing a substantive task and
  verifying tests pass, commit the changes to git and upload via `./push.cmd`.
  Do not batch unrelated work into a single commit.
- **Git commits**: When making git commits, include your AI agent name in
  the commit message (e.g., `feat(ramet) by AgentName: add numberFormat function.`).
