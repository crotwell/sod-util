package edu.sc.seis.sod.util.display;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.sc.seis.sod.mock.event.MockEventAccessOperations;
import edu.sc.seis.sod.model.event.CacheEvent;

public class EventUtilTest {

    @Test
    public void testGetEventInfoCacheEventString() {
        CacheEvent ce = MockEventAccessOperations.createEvent();
        String s = EventUtil.getEventInfo(ce);
        System.out.println(s);
        assertEquals("Event: Central Alaska | 1970-01-01T00:00:00Z | Mag: 5.0 | Depth 0.00 km | (0.0, 0.0)", s);
    }

}
