package org.pepsoft.worldpainter.tools.scripts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded, read-only drainage proposals. These courses describe the original
 * terrain; they are not permission to carve, fill a depression, or cross a mask.
 */
final class RiverDrainageCandidates {
    RiverDrainageCandidates(int columns, int rows, int step, float[] heights, boolean[] blocked,
                            boolean[] terminals, boolean[] eligibleSources, Runnable cancel) {
        final long cells = (long) columns * rows;
        if (columns <= 0 || rows <= 0 || step <= 0 || cells > MAX_CELLS
                || heights == null || blocked == null || terminals == null || eligibleSources == null
                || heights.length != cells || blocked.length != cells || terminals.length != cells
                || eligibleSources.length != cells) throw new IllegalArgumentException("Invalid drainage grid");
        this.columns = columns;
        this.rows = rows;
        this.step = step;
        this.cancel = cancel;
        check();
        this.heights = heights.clone();
        this.blocked = blocked.clone();
        this.terminals = terminals.clone();
        this.eligibleSources = eligibleSources.clone();
        final int size = heights.length;
        parent = new int[size];
        outlet = new int[size];
        distance = new double[size];
        contributing = new int[size];
        order = new int[size];
        Arrays.fill(parent, -1);
        Arrays.fill(outlet, -1);
        for (int index = 0; index < size; index++) {
            check();
            if (!this.blocked[index] && !Float.isFinite(this.heights[index])) {
                throw new IllegalArgumentException("Unblocked drainage heights must be finite");
            }
        }
        chooseDownhillParents();
        drainFlats();
        orderSize = accumulateAndMeasure();
    }

    /** Length and separation are world blocks; heights and drop use the supplied elevation datum. */
    List<Course> candidates(int limit, double minLength, double minDrop, double minSourceHeight, double separation) {
        if (limit < 0 || limit > MAX_COURSES || !finiteNonnegative(minLength) || !finiteNonnegative(minDrop)
                || !finiteNonnegative(separation) || Double.isNaN(minSourceHeight)) {
            throw new IllegalArgumentException("Invalid drainage candidate limits");
        }
        check();
        if (limit == 0) return List.of();
        final List<Course> ranked = new ArrayList<>();
        for (int index = 0; index < parent.length; index++) {
            check();
            if (blocked[index] || terminals[index] || !eligibleSources[index] || outlet[index] < 0
                    || heights[index] < minSourceHeight || distance[index] < minLength) continue;
            final double drop = (double) heights[index] - heights[outlet[index]];
            if (drop < minDrop) continue;
            ranked.add(new Course(index, outlet[index], distance[index], drop, contributing[index]));
        }
        // A source with a few cells draining into it is a better headwater
        // proposal than an isolated ridge or world-edge cell. This is only a
        // preference: cell counts depend on sampling resolution, so low-support
        // courses retain a bounded share of the result and remain eligible.
        final Comparator<Course> ranking = Comparator.comparingInt((Course course) -> supported(course) ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(Course::length).reversed())
                .thenComparing(Comparator.comparingDouble(Course::drop).reversed())
                .thenComparing(Comparator.comparingInt(Course::contributingCells).reversed())
                .thenComparingInt(Course::source).thenComparingInt(Course::outlet);
        ranked.sort(ranking);

        final List<Course> result = new ArrayList<>(limit);
        final boolean[] redundant = new boolean[parent.length], selectedPath = new boolean[parent.length];
        final int[] firstShared = new int[parent.length];
        final int lowSupportReserve = limit == MAX_COURSES ? Math.min(6, limit / 4) : Math.min(1, limit / 4);
        final int preferredLimit = limit - lowSupportReserve;

        // Fill the preferred tier first, but leave explicit room for a few
        // low-support alternatives. The router can therefore recover when the
        // sampled drainage graph gives a real headwater fewer than four cells.
        for (Course candidate : ranked) {
            check();
            if (supported(candidate) && result.size() < preferredLimit) {
                select(candidate, separation, result, redundant, selectedPath, firstShared);
            }
        }
        // An absent preferred tier must not reduce the caller's quota.
        for (Course candidate : ranked) {
            check();
            if (!supported(candidate) && result.size() < limit) {
                select(candidate, separation, result, redundant, selectedPath, firstShared);
            }
        }
        // When fewer low-support alternatives survive the shared-corridor and
        // source-separation checks, give their unused reserved slots back.
        for (Course candidate : ranked) {
            check();
            if (supported(candidate) && result.size() < limit) {
                select(candidate, separation, result, redundant, selectedPath, firstShared);
            }
        }
        result.sort(ranking);
        return List.copyOf(result);
    }

    /** Returns a complete source-to-terminal path, or empty when the point budget cannot hold it. */
    int[] path(Course course, int maxPoints) {
        if (course == null || maxPoints < 1 || maxPoints > MAX_CELLS || course.source < 0
                || course.source >= parent.length || outlet[course.source] < 0
                || course.outlet != outlet[course.source]) throw new IllegalArgumentException("Invalid drainage course");
        final int[] path = new int[Math.min(maxPoints, parent.length)];
        int count = 0;
        for (int index = course.source; index >= 0 && count < path.length; index = parent[index]) {
            check();
            path[count++] = index;
            if (index == course.outlet) return Arrays.copyOf(path, count);
        }
        return new int[0];
    }

    private void chooseDownhillParents() {
        for (int index = 0; index < parent.length; index++) {
            check();
            if (blocked[index] || terminals[index]) continue;
            double bestSlope = 0;
            for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                final int neighbour = neighbour(index, direction);
                if (neighbour < 0) continue;
                final double drop = (double) heights[index] - heights[neighbour];
                final double slope = drop / (direction < 4 ? 1 : Math.sqrt(2));
                // Strict comparison preserves cardinal-first, then fixed-index
                // direction order when two physical downhill slopes are equal.
                if (slope > bestSlope) {
                    bestSlope = slope;
                    parent[index] = neighbour;
                }
            }
        }
    }

    private void drainFlats() {
        final boolean[] visited = new boolean[parent.length];
        final int[] component = new int[parent.length], queue = new int[parent.length];
        final int[] flatDistance = new int[parent.length];
        Arrays.fill(flatDistance, -1);
        for (int start = 0; start < parent.length; start++) {
            check();
            if (blocked[start] || visited[start]) continue;
            int count = 1;
            component[0] = start;
            visited[start] = true;
            for (int position = 0; position < count; position++) {
                check();
                final int index = component[position];
                for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                    final int next = neighbour(index, direction);
                    if (next < 0 || visited[next] || heights[next] != heights[start]) continue;
                    visited[next] = true;
                    component[count++] = next;
                }
            }

            int tail = 0;
            for (int position = 0; position < count; position++) {
                final int index = component[position];
                if (terminals[index] || parent[index] >= 0) {
                    flatDistance[index] = 0;
                    queue[tail++] = index;
                }
            }
            // Without a lower receiver or legal terminal, this plateau is a
            // closed sink. Do not manufacture a spillway or change its height.
            for (int head = 0; head < tail; head++) {
                check();
                final int index = queue[head];
                for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                    final int next = neighbour(index, direction);
                    if (next < 0 || flatDistance[next] >= 0 || heights[next] != heights[start]) continue;
                    flatDistance[next] = flatDistance[index] + 1;
                    parent[next] = index;
                    queue[tail++] = next;
                }
            }
        }
    }

    private int accumulateAndMeasure() {
        final int[] incoming = new int[parent.length], queue = new int[parent.length];
        int active = 0;
        for (int index = 0; index < parent.length; index++) {
            check();
            if (blocked[index]) continue;
            active++;
            contributing[index] = 1;
            if (parent[index] >= 0) incoming[parent[index]]++;
        }
        int tail = 0;
        for (int index = 0; index < parent.length; index++) {
            if (!blocked[index] && incoming[index] == 0) queue[tail++] = index;
        }
        int count = 0;
        for (int head = 0; head < tail; head++) {
            check();
            final int index = queue[head], next = parent[index];
            order[count++] = index;
            if (next < 0) continue;
            contributing[next] += contributing[index];
            if (--incoming[next] == 0) queue[tail++] = next;
        }
        if (count != active) throw new IllegalStateException("Drainage parent cycle");
        for (int position = count - 1; position >= 0; position--) {
            check();
            final int index = order[position], next = parent[index];
            if (terminals[index]) {
                outlet[index] = index;
            } else if (next >= 0 && outlet[next] >= 0) {
                outlet[index] = outlet[next];
                distance[index] = gridDistance(index, next) + distance[next];
            }
        }
        return count;
    }

    private void excludeSharedCorridors(Course selected, double separation, boolean[] redundant,
                                        boolean[] selectedPath, int[] firstShared) {
        Arrays.fill(selectedPath, false);
        for (int index = selected.source; index >= 0; index = parent[index]) {
            check();
            selectedPath[index] = true;
            if (index == selected.outlet) break;
        }
        Arrays.fill(firstShared, -1);
        final double independentReach = Math.max(4.0 * step, separation);
        // Reverse topological order gives every cell its first shared receiver
        // in one bounded pass, instead of walking every possible full path.
        for (int position = orderSize - 1; position >= 0; position--) {
            check();
            final int index = order[position], next = parent[index];
            final int shared = selectedPath[index] ? index : next >= 0 ? firstShared[next] : -1;
            firstShared[index] = shared;
            if (shared < 0 || distance[shared] == 0) continue;
            final double common = distance[shared];
            final boolean nested = shared == index || shared == selected.source;
            final boolean shortBranches = common >= Math.min(distance[index], selected.length) * 0.75
                    && distance[index] - common < independentReach
                    && selected.length - common < independentReach;
            if (nested || shortBranches) redundant[index] = true;
        }
    }

    private void select(Course candidate, double separation, List<Course> result, boolean[] redundant,
                        boolean[] selectedPath, int[] firstShared) {
        if (redundant[candidate.source]) return;
        for (Course selected : result) {
            if (gridDistance(candidate.source, selected.source) < separation) return;
        }
        result.add(candidate);
        excludeSharedCorridors(candidate, separation, redundant, selectedPath, firstShared);
    }

    private static boolean supported(Course course) {
        return course.contributingCells >= MIN_SUPPORTED_CELLS;
    }

    private int neighbour(int index, int direction) {
        final int x = index % columns, y = index / columns;
        final int dx = DIRECTIONS[direction][0], dy = DIRECTIONS[direction][1];
        final int nx = x + dx, ny = y + dy;
        if (nx < 0 || nx >= columns || ny < 0 || ny >= rows) return -1;
        final int next = ny * columns + nx;
        if (blocked[next]) return -1;
        if (dx != 0 && dy != 0 && (blocked[y * columns + nx] || blocked[ny * columns + x])) return -1;
        return next;
    }

    private double gridDistance(int a, int b) {
        return Math.hypot(a % columns - b % columns, a / columns - b / columns) * step;
    }

    private void check() { if (cancel != null) cancel.run(); }
    private static boolean finiteNonnegative(double value) { return Double.isFinite(value) && value >= 0; }

    record Course(int source, int outlet, double length, double drop, int contributingCells) { }

    private final int columns, rows, step, orderSize;
    private final float[] heights;
    private final boolean[] blocked, terminals, eligibleSources;
    private final Runnable cancel;
    private final int[] parent, outlet, contributing, order;
    private final double[] distance;
    private static final int MAX_CELLS = 65_536, MAX_COURSES = 24, MIN_SUPPORTED_CELLS = 4;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
}
