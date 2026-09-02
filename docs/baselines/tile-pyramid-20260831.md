# Viewport tile pyramid baseline note

- Date: 2026-08-31
- Change: [`WPTileProvider`](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/WPTileProvider.java) uses [`TilePyramidCache`](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/view/TilePyramidCache.java) for zoomed-out paints (LOD from WP zoom), with LRU eviction and dirty invalidation.
- Not claimed: any specific FPS number. Measure pan/zoom subjectively on a large world after install.
