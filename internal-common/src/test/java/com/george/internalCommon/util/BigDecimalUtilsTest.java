package com.george.internalCommon.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BigDecimalUtils}.
 *
 * Naming convention: should_ExpectedResult_when_Condition
 * Each test method verifies exactly one behavior.
 */
class BigDecimalUtilsTest {

    // ======================== add() ========================

    @Test
    @DisplayName("Addition: two positive decimals with precision")
    void should_addCorrectly_when_twoPositiveNumbers() {
        // Classic floating-point trap: 0.1 + 0.2 == 0.30000000000000004 in raw Java
        double result = BigDecimalUtils.add(0.1, 0.2);
        assertEquals(0.3, result, 1e-10);
    }

    @Test
    @DisplayName("Addition: positive plus negative")
    void should_addCorrectly_when_positiveAndNegative() {
        double result = BigDecimalUtils.add(10.5, -3.3);
        assertEquals(7.2, result, 1e-10);
    }

    @Test
    @DisplayName("Addition: adding zero returns same value")
    void should_returnSameValue_when_addZero() {
        double result = BigDecimalUtils.add(99.99, 0.0);
        assertEquals(99.99, result, 1e-10);
    }

    // ======================== subtract() ========================

    @Test
    @DisplayName("Subtraction: normal positive result")
    void should_subtractCorrectly_when_normalNumbers() {
        double result = BigDecimalUtils.subtract(10.0, 3.5);
        assertEquals(6.5, result, 1e-10);
    }

    @Test
    @DisplayName("Subtraction: result is negative when subtracting larger from smaller")
    void should_returnNegative_when_subtractLargerFromSmaller() {
        // Real scenario: startMile(3km) - actualDistance(5km) = -2
        // calculatePrice uses this to determine if extra mileage fee applies
        double result = BigDecimalUtils.subtract(3.0, 5.0);
        assertEquals(-2.0, result, 1e-10);
    }

    // ======================== multiply() ========================

    @Test
    @DisplayName("Multiplication: normal calculation")
    void should_multiplyCorrectly_when_normalNumbers() {
        // Simulates: 2km excess distance * $2.50/km = $5.00
        double result = BigDecimalUtils.multiply(2.0, 2.5);
        assertEquals(5.0, result, 1e-10);
    }

    @Test
    @DisplayName("Multiplication: multiplying by zero returns zero")
    void should_returnZero_when_multiplyByZero() {
        // When distance does not exceed start mile, toll is 0 * rate = 0
        double result = BigDecimalUtils.multiply(0.0, 2.5);
        assertEquals(0.0, result, 1e-10);
    }

    // ======================== divide() ========================

    @Test
    @DisplayName("Division: evenly divisible")
    void should_divideCorrectly_when_evenlyDivisible() {
        // 6000 meters / 1000 = 6.00 km
        double result = BigDecimalUtils.divide(6000, 1000);
        assertEquals(6.0, result, 1e-10);
    }

    @Test
    @DisplayName("Division: rounds to two decimal places when not evenly divisible")
    void should_roundToTwoDecimalPlaces_when_notEvenlyDivisible() {
        // 5000 / 3 = 1666.666... -> rounds to 1666.67
        double result = BigDecimalUtils.divide(5000, 3);
        assertEquals(1666.67, result, 1e-10);
    }

    @Test
    @DisplayName("Division: throws exception when divisor is zero or negative")
    void should_throwException_when_divideByZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            BigDecimalUtils.divide(100, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            BigDecimalUtils.divide(100, -1);
        });
    }

    @Test
    @DisplayName("Division: zero dividend returns zero")
    void should_returnZero_when_dividendIsZero() {
        // 0 meters / 1000 = 0.00 km
        double result = BigDecimalUtils.divide(0, 1000);
        assertEquals(0.0, result, 1e-10);
    }
}
