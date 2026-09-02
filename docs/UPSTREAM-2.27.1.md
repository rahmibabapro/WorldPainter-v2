# Upstream 2.27.1 compatibility branch

## Branch

- Name: `feature/upstream-2.27.1`
- Base: `origin/worldpainter-v2` (without the Track-1 perf WIP pile)
- Policy: **do not** squash Track-1 performance work into the same PR as this upstream slice

## Applied upstream commits (via `git apply` / cherry-pick equivalents)

| Commit     | Notes |
|------------|--------|
| `9a4cc26d` | Applied |
| `8de418a6` | Applied |
| `daa2531d` | Spawn-related (as needed) |
| `9c357406` | Applied |
| `7546cf1e` | Applied |
| `d0891cfd` | **Skipped** — release version bump; keep v2 `SNAPSHOT` |

Manual fix: `Java261Level.worldGenSettings` field after reject hunk.

## Stash

WIP on this branch may live in stash as `upstream-2.27.1-applied-wip`. Pop onto `feature/upstream-2.27.1` only when promoting upstream; keep perf work on `worldpainter-v2`.

## Reference patches

- `docs/patches/upstream-spawn-26.1.patch` (spawn pair reference)

## Smoke

After promoting: FO Anvil export smoke; WPCore unit tests; `build-v2.ps1` only after a mergeable slice.
