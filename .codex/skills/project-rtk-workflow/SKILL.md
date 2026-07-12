---
name: project-rtk-workflow
description: Inspect KitchenTimer with compact command output and RTK-style discipline. Use when gathering repository context, checking git state, listing files, summarizing diffs, reading command output, or keeping Codex context small before implementation or review work in this project.
---

# Project RTK Workflow

Use this skill for compact project inspection. It complements the global `$rtk-token-saver` skill and the local `RTK.md`.

## Workflow

1. Read `RTK.md` if the task is mostly inspection.
2. Prefer `rg --files` for file lists.
3. Prefer `rg` for text search.
4. Use narrow `sed -n` ranges for known files.
5. Summarize command output in Russian instead of dumping logs.

## Useful Commands

- Project files: `rg --files`
- Kotlin files: `rg --files -g '*.kt' -g '*.kts'`
- Gradle config: `rg --files -g 'build.gradle.kts' -g 'settings.gradle.kts' -g 'libs.versions.toml' -g 'gradle.properties'`
- Git state: `git status --short`
- Diff summary: `git diff --stat`

## Reference

Read `references/output-discipline.md` when command output risks becoming noisy.
