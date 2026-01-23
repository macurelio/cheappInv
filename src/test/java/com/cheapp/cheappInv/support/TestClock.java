package com.cheapp.cheappInv.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public final class TestClock {
	private TestClock() {
	}

	public static Clock fixedUtc() {
		return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
	}
}
