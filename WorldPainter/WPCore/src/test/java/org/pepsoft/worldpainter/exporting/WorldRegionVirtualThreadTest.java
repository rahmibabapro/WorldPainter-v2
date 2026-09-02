package org.pepsoft.worldpainter.exporting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pepsoft.minecraft.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class WorldRegionVirtualThreadTest {
    private String prevVtProperty;

    @Before
    public void setUp() {
        ExportTestSupport.ensureReady();
        prevVtProperty = System.getProperty("org.pepsoft.worldpainter.export.virtualThreads");
    }

    @After
    public void tearDown() {
        if (prevVtProperty != null) {
            System.setProperty("org.pepsoft.worldpainter.export.virtualThreads", prevVtProperty);
        } else {
            System.clearProperty("org.pepsoft.worldpainter.export.virtualThreads");
        }
    }

    @Test
    public void testVirtualThreadSaveSuccess() throws Exception {
        System.setProperty("org.pepsoft.worldpainter.export.virtualThreads", "true");
        final int chunkCount = 64;
        final List<Chunk> toSave = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            toSave.add(new MemoryChunk(i, i % 32, -64, 320));
        }

        final CountingChunkStore chunkStore = new CountingChunkStore();
        chunkStore.doInTransaction(() -> {
            try {
                java.lang.reflect.Method method = WorldRegion.class.getDeclaredMethod("saveWithVirtualThreadsOrThrow", ChunkStore.class, List.class);
                method.setAccessible(true);
                method.invoke(null, chunkStore, toSave);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("All chunks must be successfully saved via virtual threads", chunkCount, chunkStore.savedCount.get());
        assertTrue("Transaction must have executed", chunkStore.transactionRun.get());
    }

    @Test
    public void testVirtualThreadErrorInjectionFailsFastNoDoubleWrite() {
        System.setProperty("org.pepsoft.worldpainter.export.virtualThreads", "true");
        final int chunkCount = 32;
        final int failAtChunkIndex = 5;
        final FailingChunkStore failingStore = new FailingChunkStore(failAtChunkIndex);

        try {
            final List<Chunk> toSave = new ArrayList<>();
            for (int i = 0; i < chunkCount; i++) {
                toSave.add(new MemoryChunk(i, 0, -64, 320));
            }

            failingStore.doInTransaction(() -> {
                try {
                    java.lang.reflect.Method method = WorldRegion.class.getDeclaredMethod("saveWithVirtualThreadsOrThrow", ChunkStore.class, List.class);
                    method.setAccessible(true);
                    method.invoke(null, failingStore, toSave);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (target instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(target);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            fail("Should have thrown an exception on error-injection");
        } catch (RuntimeException e) {
            assertTrue("Exception should relate to chunk save failure or injected error",
                    e.getMessage().contains("Chunk save failed") || e.getMessage().contains("Injected failure") || (e.getCause() != null && e.getCause().getMessage().contains("Injected failure")));
        }

        assertEquals("Transaction should only have been attempted once", 1, failingStore.transactionAttempts.get());
    }

    private static class CountingChunkStore implements ChunkStore {
        final AtomicInteger savedCount = new AtomicInteger(0);
        final AtomicBoolean transactionRun = new AtomicBoolean(false);

        @Override
        public void doInTransaction(Runnable task) {
            transactionRun.set(true);
            task.run();
        }

        @Override
        public void saveChunk(Chunk chunk) {
            savedCount.incrementAndGet();
        }

        @Override
        public void flush() {}

        @Override
        public int getChunkCount() { return savedCount.get(); }

        @Override
        public Set<MinecraftCoords> getChunkCoords() { return Collections.emptySet(); }

        @Override
        public boolean visitChunks(ChunkVisitor visitor) { return true; }

        @Override
        public boolean visitChunksForEditing(ChunkVisitor visitor) { return true; }

        @Override
        public Chunk getChunk(int x, int z) { return null; }

        @Override
        public Chunk getChunkForEditing(int x, int z) { return null; }

        @Override
        public boolean isChunkPresent(int x, int z) { return false; }

        @Override
        public void close() { }
    }

    private static class FailingChunkStore implements ChunkStore {
        final int failAtIndex;
        final AtomicInteger savedCount = new AtomicInteger(0);
        final AtomicInteger transactionAttempts = new AtomicInteger(0);

        public FailingChunkStore(int failAtIndex) {
            this.failAtIndex = failAtIndex;
        }

        @Override
        public void doInTransaction(Runnable task) {
            transactionAttempts.incrementAndGet();
            task.run();
        }

        @Override
        public void saveChunk(Chunk chunk) {
            int current = savedCount.incrementAndGet();
            if (current == failAtIndex) {
                throw new IllegalStateException("Injected failure at chunk " + current);
            }
        }

        @Override
        public void flush() {}

        @Override
        public int getChunkCount() { return savedCount.get(); }

        @Override
        public Set<MinecraftCoords> getChunkCoords() { return Collections.emptySet(); }

        @Override
        public boolean visitChunks(ChunkVisitor visitor) { return true; }

        @Override
        public boolean visitChunksForEditing(ChunkVisitor visitor) { return true; }

        @Override
        public Chunk getChunk(int x, int z) { return null; }

        @Override
        public Chunk getChunkForEditing(int x, int z) { return null; }

        @Override
        public boolean isChunkPresent(int x, int z) { return false; }

        @Override
        public void close() { }
    }
}
