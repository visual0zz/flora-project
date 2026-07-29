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


## Build & Test Commands

- `./action/test.cmd` — Run all unit tests (Maven, fast)
- `./action/test-slow.cmd` — Slow tests: Maven tests tagged `@Tag("slow")`
  plus IntelliJ plugin sandbox fixture tests
- `./action/produce.cmd` — Full build without tests.
- `./action/regenerate.cmd` — Regenerate code from templates
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
  `addition/codereview/`. Naming: `review{YYYYMMDD}-{NN}-{subject}.md`.
- **Planning**: Store AI-generated plan or design documents in
  `addition/design/`. Naming: `idea{YYYYMMDD}-{NN}-{subject}.md`.
- **Decision**: Whenever the agent makes a decision (e.g., technology selection or
  implementation approach), record it in `addition/decision/`.
  Naming: `decision{YYYYMMDD}-{NN}-{module}.md`.
- **Changelog**: If a submodule contains a `CHANGELOG.md` file, update it
  after each code change to reflect what was modified, added, or removed.
- **All OS console scripts must be pure English.** This includes every
  cross-platform script such as `*.cmd`, `*.sh`, `*.ps1`, and any shell
  helper invoked from the terminal. Do **not** put Chinese (or any
  non-ASCII) text in code, comments, `echo`/`printf` strings, or labels.
  Reason: on Windows, `cmd.exe` reads `.cmd` files in the system codepage
  (e.g. GBK). UTF-8 Chinese makes `for /f "eol=#"` and other parsing
  silently skip all lines, breaking the script. Commit messages may still
  contain Chinese — mojibake there is harmless.
- **Every file under `addition/config/` must be pure English** (ASCII only),
  including comments inside `remoteRepoList.txt`, `pushConfig.txt`,
  `tagPrefixes.txt`, etc. The same codepage trap applies: a Chinese
  comment in a config file consumed by `cmd` can disable the whole file
  read. Keep keys, values, and comments in English.

## Code Style Requirements

- **Two-level semantic package layout for base utility modules**: Modules like
  `flora-root` (zero-dependency foundation libraries) must organize packages by
  a two-tier semantic hierarchy under `com.flora`:
  - The first level denotes a broad category (e.g. `com.flora.collect`,
    `com.flora.text`).
  - The second level denotes a more specific sub-category within that category.
  - Only export (via `module-info.java` `exports`) the packages that are meant
    to be consumed by external code. When a category package mixes exportable
    and internal types, move the internal types into a dedicated `impl`
    sub-package (e.g. `com.flora.collect.impl`) and keep the parent package
    containing only the public API surface.
- **Comments describe contracts and behavior, not history**: Code comments must
  focus on the code's *conventions, actual runtime behavior, and externally
  observable functionality*. Do not use comments to record evolution history,
  changelogs, or comparisons describing how the implementation differs from
  alternative approaches. Keep comments forward-looking and about what the code
  *is and does*, not how it came to be.