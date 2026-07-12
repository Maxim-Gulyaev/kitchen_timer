# Timer State Machine

## Suggested Types

- `TimerPreset`: stable id, display label, duration.
- `TimerStatus`: `Idle`, `Running`, `Paused`, `Finished`.
- `TimerState`: selected preset, total duration, remaining duration, status, progress.
- `TimerIntent`: start, pause, resume, reset, select preset, add time.

## Transition Notes

- `Start` from idle uses the selected duration.
- `Pause` preserves remaining time.
- `Resume` continues from preserved remaining time.
- `Reset` restores selected duration.
- `Tick` clamps at zero and moves to finished exactly once.
- Selecting presets while running must be disabled or explicitly defined as reset-and-select.

## Side Effects

Notification, sound, and haptic feedback should observe domain transitions. They should not live inside pure reducer logic.
