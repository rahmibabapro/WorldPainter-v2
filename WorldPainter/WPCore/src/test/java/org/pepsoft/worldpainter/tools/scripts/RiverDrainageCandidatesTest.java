package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RiverDrainageCandidatesTest {
    @Test
    public void followsTheValleyFromHeadwaterToTheSuppliedOutlet() {
        Fixture fixture = new Fixture(5, 3, 4, new float[] {
                20, 20, 20, 20, 20,
                10, 8, 6, 4, 2,
                20, 20, 20, 20, 20
        });
        fixture.sources[5] = true;
        fixture.terminals[9] = true;

        RiverDrainageCandidates drainage = fixture.build();
        RiverDrainageCandidates.Course course = onlyCourse(drainage);

        assertEquals(5, course.source());
        assertEquals(9, course.outlet());
        assertEquals(16.0, course.length(), 0.000001);
        assertEquals(8.0, course.drop(), 0.000001);
        assertArrayEquals(new int[] {5, 6, 7, 8, 9}, drainage.path(course, 16));
    }

    @Test
    public void choosesDownhillGradientInsteadOfTheLowestNeighbour() {
        Fixture fixture = new Fixture(3, 3, 4, new float[] {
                10, 8, 20,
                20, 7.3f, 20,
                20, 20, 0
        });
        fixture.sources[0] = true;
        fixture.terminals[4] = true;

        RiverDrainageCandidates drainage = fixture.build();
        RiverDrainageCandidates.Course course = onlyCourse(drainage);

        // The cardinal drop of 2 is steeper than a diagonal drop of 2.7.
        // The supplied outlet must stop the chain even with lower land beyond it.
        assertArrayEquals(new int[] {0, 1, 4}, drainage.path(course, 16));
        assertEquals(8.0, course.length(), 0.000001);
        assertEquals(2.7, course.drop(), 0.000001);
    }

    @Test
    public void leavesAnEnclosedPitUnresolved() {
        float[] heights = new float[25];
        Arrays.fill(heights, 4);
        heights[12] = 1;
        heights[24] = 0;
        Fixture fixture = new Fixture(5, 5, 1, heights);
        fixture.sources[12] = true;
        fixture.terminals[24] = true;

        assertTrue(allCourses(fixture.build()).isEmpty());
    }

    @Test
    public void resolvesAnEqualHeightPlateauTowardItsRealLowerExit() {
        Fixture fixture = new Fixture(6, 1, 2, new float[] {10, 10, 10, 10, 9, 8});
        fixture.sources[0] = true;
        fixture.terminals[5] = true;

        RiverDrainageCandidates drainage = fixture.build();
        RiverDrainageCandidates.Course course = onlyCourse(drainage);

        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, drainage.path(course, 16));
        assertEquals(10.0, course.length(), 0.000001);
        assertEquals(2.0, course.drop(), 0.000001);
    }

    @Test
    public void resolvesAPlateauToAnEqualHeightTerminalButDoesNotInventAnExit() {
        Fixture fixture = new Fixture(4, 1, 1, new float[] {10, 10, 10, 10});
        fixture.sources[0] = true;
        fixture.terminals[3] = true;
        RiverDrainageCandidates drainage = fixture.build();
        assertArrayEquals(new int[] {0, 1, 2, 3}, drainage.path(onlyCourse(drainage), 8));

        fixture.terminals[3] = false;
        assertTrue(allCourses(fixture.build()).isEmpty());
    }

    @Test
    public void cannotCutDiagonallyAcrossEitherBlockedCorner() {
        for (int mask = 1; mask <= 3; mask++) {
            Fixture fixture = new Fixture(2, 2, 1, new float[] {10, 20, 20, 0});
            fixture.sources[0] = true;
            fixture.terminals[3] = true;
            fixture.blocked[1] = (mask & 1) != 0;
            fixture.blocked[2] = (mask & 2) != 0;

            assertTrue("Blocked corner mask " + mask, allCourses(fixture.build()).isEmpty());
        }
    }

    @Test
    public void ranksTheLongCourseAheadOfANearbyShortExit() {
        Fixture fixture = separatedCourses();
        RiverDrainageCandidates drainage = fixture.build();
        List<RiverDrainageCandidates.Course> courses = allCourses(drainage);

        assertEquals(2, courses.size());
        assertEquals(0, courses.get(0).source());
        assertEquals(16, courses.get(1).source());
        assertEquals(56.0, courses.get(0).length(), 0.000001);
        assertEquals(8.0, courses.get(1).length(), 0.000001);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                drainage.path(courses.get(0), 16));
        assertArrayEquals(new int[] {16, 17}, drainage.path(courses.get(1), 16));
    }

    @Test
    public void ranksASupportedHeadwaterAheadOfALongerIsolatedSpur() {
        Fixture fixture = tieredCourses(1, 1);
        RiverDrainageCandidates drainage = fixture.build();
        List<RiverDrainageCandidates.Course> courses = allCourses(drainage);

        assertEquals(2, courses.size());
        assertEquals(3, courses.get(0).source());
        assertEquals(4, courses.get(0).contributingCells());
        assertEquals(6.0, courses.get(0).length(), 0.000001);
        assertEquals(20, courses.get(1).source());
        assertEquals(1, courses.get(1).contributingCells());
        assertEquals(9.0, courses.get(1).length(), 0.000001);
    }

    @Test
    public void reservesLowSupportAlternativesWithoutReducingSingleTierResults() {
        RiverDrainageCandidates mixed = tieredCourses(24, 10).build();

        List<RiverDrainageCandidates.Course> full = mixed.candidates(24, 0, 0,
                Double.NEGATIVE_INFINITY, 0);
        assertEquals(24, full.size());
        assertEquals(18, full.stream().filter(course -> course.contributingCells() >= 4).count());
        assertEquals(6, full.stream().filter(course -> course.contributingCells() < 4).count());
        assertTrue(full.subList(0, 18).stream().allMatch(course -> course.contributingCells() >= 4));

        List<RiverDrainageCandidates.Course> ordinaryLimit = mixed.candidates(8, 0, 0,
                Double.NEGATIVE_INFINITY, 0);
        assertEquals(8, ordinaryLimit.size());
        assertEquals(7, ordinaryLimit.stream().filter(course -> course.contributingCells() >= 4).count());
        assertEquals(1, ordinaryLimit.stream().filter(course -> course.contributingCells() < 4).count());

        List<RiverDrainageCandidates.Course> scarceLowSupport = tieredCourses(24, 1).build()
                .candidates(24, 0, 0, Double.NEGATIVE_INFINITY, 0);
        assertEquals(24, scarceLowSupport.size());
        assertTrue(scarceLowSupport.subList(0, 23).stream()
                .allMatch(course -> course.contributingCells() >= 4));
        assertEquals(1, scarceLowSupport.get(23).contributingCells());

        assertEquals(24, tieredCourses(30, 0).build().candidates(24, 0, 0,
                Double.NEGATIVE_INFINITY, 0).size());
        assertEquals(24, tieredCourses(0, 30).build().candidates(24, 0, 0,
                Double.NEGATIVE_INFINITY, 0).size());
    }

    @Test
    public void appliesCourseLengthDescentAndSourceHeightRequirements() {
        RiverDrainageCandidates drainage = separatedCourses().build();

        List<RiverDrainageCandidates.Course> courses = drainage.candidates(24, 16, 2, 5, 0);
        assertEquals(1, courses.size());
        assertEquals(0, courses.get(0).source());
        assertTrue(drainage.candidates(24, 57, 0, 0, 0).isEmpty());
        assertTrue(drainage.candidates(24, 0, 8, 0, 0).isEmpty());
        assertTrue(drainage.candidates(24, 0, 0, 11, 0).isEmpty());
    }

    @Test
    public void constructionAndRepeatedQueriesAreDeterministic() {
        Fixture fixture = new Fixture(5, 3, 1, new float[] {
                10, 10, 10, 10, 9,
                10, 10, 10, 10, 8,
                10, 10, 10, 10, 9
        });
        fixture.sources[0] = true;
        fixture.sources[10] = true;
        fixture.terminals[9] = true;
        RiverDrainageCandidates first = fixture.build();
        RiverDrainageCandidates second = fixture.build();
        List<RiverDrainageCandidates.Course> expected = allCourses(first);

        assertFalse(expected.isEmpty());
        assertEquals(expected, allCourses(first));
        assertEquals(expected, allCourses(second));
        for (RiverDrainageCandidates.Course course : expected) {
            assertArrayEquals(first.path(course, 32), second.path(course, 32));
        }
    }

    @Test
    public void suppressesNestedStartsOnTheSameSelectedCourse() {
        Fixture fixture = new Fixture(6, 1, 1, new float[] {10, 9, 8, 7, 6, 5});
        fixture.sources[0] = true;
        fixture.sources[2] = true;
        fixture.terminals[5] = true;

        assertEquals(0, onlyCourse(fixture.build()).source());
    }

    @Test
    public void retainsIndependentTributariesThatShareAnOutlet() {
        float[] heights = new float[35];
        Fixture fixture = new Fixture(5, 7, 1, heights);
        Arrays.fill(fixture.blocked, true);
        for (int row = 0; row < 7; row++) {
            fixture.blocked[row * 5] = false;
            fixture.blocked[row * 5 + 4] = false;
            heights[row * 5] = 16 - row;
            heights[row * 5 + 4] = 16 - row;
        }
        for (int column = 0; column < 5; column++) {
            fixture.blocked[30 + column] = false;
            heights[30 + column] = 8 + Math.abs(column - 2);
        }
        fixture.sources[0] = true;
        fixture.sources[4] = true;
        fixture.terminals[32] = true;
        RiverDrainageCandidates drainage = fixture.build();
        List<RiverDrainageCandidates.Course> courses = allCourses(drainage);

        assertEquals(2, courses.size());
        assertEquals(0, courses.get(0).source());
        assertEquals(4, courses.get(1).source());
        assertEquals(32, courses.get(0).outlet());
        assertEquals(32, courses.get(1).outlet());
        assertArrayEquals(new int[] {0, 5, 10, 15, 20, 25, 30, 31, 32},
                drainage.path(courses.get(0), 16));
        assertArrayEquals(new int[] {4, 9, 14, 19, 24, 29, 34, 33, 32},
                drainage.path(courses.get(1), 16));
    }

    @Test
    public void declinesToMaterialiseATruncatedCourse() {
        Fixture fixture = new Fixture(6, 1, 1, new float[] {10, 9, 8, 7, 6, 5});
        fixture.sources[0] = true;
        fixture.terminals[5] = true;
        RiverDrainageCandidates drainage = fixture.build();
        RiverDrainageCandidates.Course course = onlyCourse(drainage);

        assertArrayEquals(new int[0], drainage.path(course, 5));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, drainage.path(course, 6));
    }

    @Test
    public void propagatesCancellationDuringConstruction() {
        Fixture fixture = new Fixture(4, 1, 1, new float[] {4, 3, 2, 1});
        fixture.sources[0] = true;
        fixture.terminals[3] = true;
        RuntimeException cancellation = new RuntimeException("cancel drainage prepass");

        try {
            new RiverDrainageCandidates(fixture.columns, fixture.rows, fixture.step,
                    fixture.heights, fixture.blocked, fixture.terminals, fixture.sources,
                    () -> { throw cancellation; });
            fail("Cancellation must be propagated");
        } catch (RuntimeException actual) {
            assertSame(cancellation, actual);
        }
    }

    @Test
    public void preservesCallerArraysAndOwnsItsInputSnapshot() {
        Fixture fixture = new Fixture(4, 1, 1, new float[] {8, 6, 4, 2});
        fixture.sources[0] = true;
        fixture.terminals[3] = true;
        float[] originalHeights = fixture.heights.clone();
        boolean[] originalBlocked = fixture.blocked.clone();
        boolean[] originalTerminals = fixture.terminals.clone();
        boolean[] originalSources = fixture.sources.clone();
        RiverDrainageCandidates drainage = fixture.build();
        List<RiverDrainageCandidates.Course> expected = allCourses(drainage);
        assertEquals(1, expected.size());
        int[] expectedPath = drainage.path(expected.get(0), 8);

        assertArrayEquals(originalHeights, fixture.heights, 0.0f);
        assertArrayEquals(originalBlocked, fixture.blocked);
        assertArrayEquals(originalTerminals, fixture.terminals);
        assertArrayEquals(originalSources, fixture.sources);

        Arrays.fill(fixture.heights, Float.NaN);
        Arrays.fill(fixture.blocked, true);
        Arrays.fill(fixture.terminals, false);
        Arrays.fill(fixture.sources, false);
        assertEquals(expected, allCourses(drainage));
        assertArrayEquals(expectedPath, drainage.path(expected.get(0), 8));
    }

    private static Fixture separatedCourses() {
        Fixture fixture = new Fixture(8, 3, 8, new float[] {
                10, 9, 8, 7, 6, 5, 4, 3,
                0, 0, 0, 0, 0, 0, 0, 0,
                10, 9, 8, 7, 6, 5, 4, 3
        });
        Arrays.fill(fixture.blocked, 8, 16, true);
        fixture.sources[0] = true;
        fixture.sources[16] = true;
        fixture.terminals[7] = true;
        fixture.terminals[17] = true;
        return fixture;
    }

    private static Fixture tieredCourses(int supported, int lowSupport) {
        int courseCount = supported + lowSupport;
        int columns = 10, rows = courseCount * 2 - 1;
        float[] heights = new float[columns * rows];
        Fixture fixture = new Fixture(columns, rows, 1, heights);
        Arrays.fill(fixture.blocked, true);
        for (int course = 0; course < courseCount; course++) {
            int rowStart = course * 2 * columns;
            for (int column = 0; column < columns; column++) {
                fixture.blocked[rowStart + column] = false;
                heights[rowStart + column] = 100 - column;
            }
            fixture.sources[rowStart + (course < supported ? 3 : 0)] = true;
            fixture.terminals[rowStart + columns - 1] = true;
        }
        return fixture;
    }

    private static List<RiverDrainageCandidates.Course> allCourses(RiverDrainageCandidates drainage) {
        return drainage.candidates(24, 0, 0, Double.NEGATIVE_INFINITY, 0);
    }

    private static RiverDrainageCandidates.Course onlyCourse(RiverDrainageCandidates drainage) {
        List<RiverDrainageCandidates.Course> courses = allCourses(drainage);
        assertEquals(1, courses.size());
        return courses.get(0);
    }

    private static final class Fixture {
        private Fixture(int columns, int rows, int step, float[] heights) {
            this.columns = columns;
            this.rows = rows;
            this.step = step;
            this.heights = heights;
            blocked = new boolean[heights.length];
            terminals = new boolean[heights.length];
            sources = new boolean[heights.length];
        }

        private RiverDrainageCandidates build() {
            return new RiverDrainageCandidates(columns, rows, step, heights,
                    blocked, terminals, sources, () -> { });
        }

        private final int columns;
        private final int rows;
        private final int step;
        private final float[] heights;
        private final boolean[] blocked;
        private final boolean[] terminals;
        private final boolean[] sources;
    }
}
