## Project Architecture

**Flora-project**: Java 26 multi-module Maven project (JPMS)

```
flora-project/            -- Root POM (pom packaging, Java 26)
├── action/               -- Dev workflow scripts (test, produce, regenerate)
├── addition/             -- Utility scripts, configs, and code review reports
├── plugins/              -- IDE & build tool plugins
│   └── maven-plugins/    -- Maven Mojo plugins
│       ├── flora-ramet-plugin/   -- Ramet codegen Mojo
│       └── flora-osmetes-plugin/ -- Encoding checker Mojo
├── absent/               -- Files not to be tracked by the repo
│   └── tmp/               -- Temporary files
├── flora-root/           -- Zero-dependency utility library
├── flora-ramet/          -- Template-based code generation engine
├── flora-osmetes/        -- Source code analysis & validation library
├── flora-garden/         -- Placeholder module
├── flora-tangle/         -- Java bytecode obfuscator
│   └── testbed/          -- Tangle integration testbed
├── flora/                -- Aggregate (root + garden)
└── flora-benchmark/      -- JMH microbenchmarks
```

### Self-Bootstrapping (Meta-Code Generation)

Template files in `flora-root/src/main/templates/*.ramet` are rendered by
the `regenerate` Maven profile (`flora-ramet-plugin` → `com.flora.codegen.Ramet`)
to produce the 64 `*FastHashMap` classes. The profile only activates during
`generate-sources` phase.

## Build & Test Commands

- `./action/test.cmd` — Run all unit tests (Maven, fast)
- `./action/test-slow.cmd` — Slow tests: Maven tests tagged `@Tag("slow")`
  plus IntelliJ plugin sandbox fixture tests
- `./action/produce.cmd` — Full build without tests.
- `./action/regenerate.cmd` — Regenerate code from templates
  (`mvnw generate-sources -Pregenerate`))
-
- `./mvnw test` — Run all tests
- `./mvnw test -Dtest=TokenTest` — Run a single test class
- `./mvnw test -Dtest=TokenTest#testVarToken` — Run a single test method
- `./mvnw clean install -DskipTests` — Full build, skip tests
-
- `./push.cmd "commit message"` — Push to all remotes in
  `addition/config/remoteRepoList.txt`. Compatible script: the `.cmd`
  extension is a convention only — it runs under both Windows (cmd.exe)
  and Unix-like shells (bash/zsh via shebang + goto fallback).

## AI Guidelines

- **Language**: Write this file in English.
- **Line width**: Wrap lines in this file at 110 characters.
- **Commit & push after each task**: After completing a substantive task and
  verifying tests pass, commit the changes to git and upload via `./push.cmd`.
  Do not batch unrelated work into a single commit.
- **Git commits**: When making git commits, include your AI agent name in
  the commit message (e.g., `feat(ramet) by AgentName: add numberFormat function.`).
- **Code review**: Store AI-generated code review reports in
  `addition/codereview/`. Naming: `review{YYYYMMDD}-{NN}.md`.
