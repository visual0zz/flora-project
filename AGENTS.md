## Project Architecture

**flora-project**: Java 26 multi-module Maven project (JPMS)

```
flora-project/            -- Root POM (pom packaging, Java 26)
├── absent/               -- Files that should be excluded from version control (gitignored)
│   └── tmp/              -- Temporary files
├── action/               -- Dev workflow scripts (test, produce, regenerate)
├── addition/             -- Utility scripts, configs, reports
│   ├── codereview/       -- code review reports
│   ├── decision/         -- decision records
│   └── design/           -- plan/design documents
├── flora/                -- Aggregate (root + garden)
├── flora-benchmark/      -- JMH microbenchmarks
├── flora-garden/         -- Placeholder module
├── flora-osmetes/        -- Source code analysis & validation library
├── flora-ramet/          -- Template-based code generation engine
├── flora-root/           -- Zero-dependency utility library
├── flora-tangle/         -- Java bytecode obfuscator
│   └── testbed/          -- Tangle integration testbed
└── plugins/              -- IDE & build tool plugins
    └── maven-plugins/    -- Maven Mojo plugins
        ├── flora-osmetes-plugin/   -- Encoding checker Mojo
        └── flora-ramet-plugin/     -- Ramet codegen Mojo
```

### Self-Bootstrapping (Meta-Code Generation)

Template files in `flora-root/src/main/templates/*.ramet` are rendered by
the `regenerate` Maven profile (`flora-ramet-plugin` → `com.flora.codegen.Ramet`)
to produce the 64 `*FastHashMap` classes. The profile is only active during
the `generate-sources` phase.

## Build & Test Commands

- `./action/test.cmd` — Run all unit tests (Maven, fast)
- `./action/test-slow.cmd` — Slow tests: Maven tests tagged `@Tag("slow")`
  plus IntelliJ plugin sandbox fixture tests
- `./action/produce.cmd` — Full build without tests.
- `./action/regenerate.cmd` — Regenerate code from templates
  (`mvnw generate-sources -Pregenerate`)
-
- `./mvnw test` — Run all tests
- `./mvnw test -Dtest=TokenTest` — Run a single test class
- `./mvnw test -Dtest=TokenTest#testVarToken` — Run a single test method
- `./mvnw clean install -DskipTests` — Full build, skip tests
-
- `./push.cmd "commit message"` — Push to all remotes listed in
  `addition/config/remoteRepoList.txt`. This is a cross-platform script: the
  `.cmd` extension is purely a convention — it runs on both Windows (cmd.exe)
  and Unix-like shells (bash/zsh, via shebang + goto fallback).

## AI Guidelines

- **Commit & push after each task**: After completing a substantive task and
  verifying that tests pass, commit the changes and upload via `./push.cmd`.
  Do not batch unrelated work into a single commit.
- **Git commits**: When making git commits, include your AI agent name in
  the commit message (e.g., `feat(ramet) by AgentName: add numberFormat function.`).
- **Code review**: Store AI-generated code review reports in
  `addition/codereview/`. Naming: `review{YYYYMMDD}-{NN}:{subject}.md`.
- **Planning**: Store AI-generated plan or design documents in
  `addition/design/`. Naming: `idea{YYYYMMDD}-{NN}:{subject}.md`.
- **Decision**: Whenever the agent makes a decision (e.g., technology selection or
  implementation approach), record it in `addition/decision/`.
  Naming: `decision{YYYYMMDD}-{NN}:{module}.md`.
