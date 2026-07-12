# RTK Workflow Notes

Use RTK when it is available and the task is mainly about inspecting command output, git state, file listings, or command history. The goal is to keep terminal context compact while preserving enough signal for implementation decisions.

## Preferred Inspection Flow

- Use `rg --files` for file lists.
- Use `rg` for text search.
- Use narrow `sed -n` ranges when reading known files.
- Prefer targeted Gradle tasks over broad project builds during iteration.

## Useful Commands

- Project files: `rg --files`
- Kotlin files: `rg --files -g '*.kt' -g '*.kts'`
- Gradle config: `rg --files -g 'build.gradle.kts' -g 'settings.gradle.kts' -g 'libs.versions.toml' -g 'gradle.properties'`
- Current git state: `git status --short`
- Recent diff summary: `git diff --stat`

## Output Discipline

- Keep command output narrow enough to fit the current debugging question.
- Do not dump full build logs into chat unless the failure needs that context.
- When reporting command results to the user, summarize the important lines in Russian.
