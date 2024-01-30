package io.github.imaron85.wakamine.wakatime;

import java.math.BigDecimal;

public class Util {
	public static BigDecimal getCurrentTimestamp() {
		return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4, BigDecimal.ROUND_HALF_UP);
	}
}
