# CLAUDE.md

See `AGENTS.md` for the full project reference (commands, architecture, testing quirks, config-cache rules).

## Claude-specific notes

- **LSP available:** `jdtls-lsp` plugin is installed — use LSP tool for Java/Groovy symbol lookup before grepping.
- **No CLAUDE.md existed before** — project docs live in `AGENTS.md`.
- **Branch:** work typically happens on `update-for-github`; PRs target `main`.
- **Groovy everywhere:** all plugin source and all tests are Groovy (not Java), even in `src/main/groovy/`.
- **Functional tests need port 1080 free** — confirm before running `./gradlew :plugin:functionalTest`.
