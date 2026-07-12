# Test Patterns

## Time-Based Logic

- Inject a clock, ticker, or countdown driver.
- Advance fake time explicitly in tests.
- Assert state after each intent or tick.
- Avoid `delay` in unit tests.

## Useful Cases

- Start from zero.
- Pause while idle.
- Resume from paused.
- Reset after running.
- Tick past zero clamps to zero.
- Completion event is emitted once.

## Test Shape

Arrange selected preset and initial state, act with one intent or tick, assert the full state and any one-shot event.
