---
name: kmp-architecture
description: Review or design Kotlin Multiplatform architecture for KitchenTimer. Use for shared/commonMain boundaries, androidMain/iosMain adapters, expect/actual contracts, source-set layout, dependency placement, composition roots, and deciding where new timer, UI, platform, or test code should live.
---

# KMP Architecture

Use this skill for architecture decisions before implementation.

## Workflow

1. Read `AGENTS.md`.
2. Inspect `shared/build.gradle.kts`, `gradle/libs.versions.toml`, and relevant source sets.
3. Decide whether the behavior is pure common logic, shared UI, or platform side effect.
4. Prefer the smallest common contract that keeps app entry points thin.
5. Report blocking risks first, then the smallest patch shape.

## Rules

- Put business rules in `shared/src/commonMain`.
- Put Android/iOS APIs behind small `expect`/`actual` contracts only when common code cannot own the behavior.
- Do not put timer semantics in `androidApp` or `iosApp`.
- Keep dependency additions in `gradle/libs.versions.toml` and use existing aliases when possible.

## Reference

Read `references/source-sets.md` when deciding file placement or source-set dependencies.
