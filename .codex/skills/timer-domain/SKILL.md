---
name: timer-domain
description: Design, implement, or review KitchenTimer countdown domain logic. Use for timer state machines, presets, start/pause/resume/reset/restart/add-time semantics, zero and finished states, elapsed-time reconciliation, fake ticker/clock abstractions, completion events, and pure common tests.
---

# Timer Domain

Use this skill whenever timer behavior or invariants are involved.

## Workflow

1. Name the states and transitions before coding.
2. Keep pure state transitions separate from platform side effects.
3. Use deterministic time input: fake clock, ticker, or elapsed-time calculation.
4. Add common tests for edge cases and invariants.
5. Verify with `./gradlew :shared:allTests` when tests exist, otherwise `./gradlew :shared:assemble`.

## Core Invariants

- Remaining time must never be negative.
- Starting from zero must not create a running timer.
- Pausing idle or finished state should be a no-op.
- Reset should restore selected preset duration and return to idle.
- Completion should fire once per run.

## Reference

Read `references/state-machine.md` before adding or changing timer behavior.
