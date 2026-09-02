# Resources cluster export baseline

- Created: 2026-08-31
- Classifier: v2 cluster ResourcesExporter (legacyNoise=false default)

| Mode | Duration (ms) | Resources (ms) | Resources % | Output (KB) | Peak Heap (MB) |
|------|---------------|----------------|-------------|-------------|----------------|
| Full Export (Deflate Default) | 1510 | 447 | 15.2 | 1032.77 | 76 |
| Full Export (Deflate Level 1) | 722 | 61 | 5.0 | 1036.77 | 65 |
| Full Export (Deflate Level 3) | 542 | 60 | 6.2 | 1032.77 | 78 |
| Turbo Export (Level 1 + Deferred) | 505 | 0 | 0.0 | 1032.77 | 69 |
| Linear Format Export (.linear) | 321 | 0 | 0.0 | 1215.33 | 73 |


### Resources stage (Full Export Deflate Default)

- Resources ms: 447
- Resources share: 15.2%
- Gate: share < 30% OR Resources ms < previous × 0.25 (prior ~2340 ms / 67%)
