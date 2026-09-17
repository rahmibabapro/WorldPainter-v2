package org.pepsoft.worldpainter.tools.scripts;

import org.junit.Test;
import java.util.NoSuchElementException;
import static org.junit.Assert.*;

public class RankedRiverSourcesTest {
    @Test public void retainsCandidatesBeyondBothLegacyLimits() {
        var queue=new RankedRiverSources(1024,Integer::compare);
        for(int i=0;i<1024;i++)queue.add(i);
        for(int i=1023;i>=0;i--)assertEquals(i,queue.remove());
        assertTrue(queue.isEmpty());
        assertThrows(NoSuchElementException.class,queue::remove);
    }
    @Test public void priorityDoesNotDependOnInsertionOrder() {
        var a=new RankedRiverSources(100,Integer::compare);
        var b=new RankedRiverSources(100,Integer::compare);
        for(int i=0;i<100;i++){a.add(i);b.add(99-i);}
        while(!a.isEmpty())assertEquals(a.remove(),b.remove());
    }
    @Test public void rejectsCapacityOverflowWithoutLosingSources() {
        var queue=new RankedRiverSources(1,Integer::compare);queue.add(7);
        assertThrows(IllegalStateException.class,()->queue.add(8));assertEquals(7,queue.remove());
    }
}
