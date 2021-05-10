package edu.sc.seis.sod.util.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.common.UnitImpl;

class TestClockUtil {

	@Test
	void test() {
		Duration d = ClockUtil.durationFrom(new QuantityImpl(10, UnitImpl.SECOND));
		assertEquals(10, d.getSeconds());
		assertEquals(0, d.getNano());
		d = ClockUtil.durationFrom(new QuantityImpl(10.1, UnitImpl.SECOND));
		assertEquals(10, d.getSeconds());
		assertEquals(100000000, d.getNano());
	}

}
