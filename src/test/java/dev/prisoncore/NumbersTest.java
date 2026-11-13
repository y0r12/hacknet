package dev.prisoncore;

import dev.prisoncore.util.Numbers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumbersTest {

    @Test
    void formatsShortMoney() {
        assertEquals("0", Numbers.format(0));
        assertEquals("999", Numbers.format(999));
        assertEquals("12.5", Numbers.format(12.5));
        assertEquals("1K", Numbers.format(1000));
        assertEquals("1.5K", Numbers.format(1500));
        assertEquals("2.35M", Numbers.format(2_350_000));
        assertEquals("42M", Numbers.format(42_000_000));
        assertEquals("1B", Numbers.format(1e9));
        assertEquals("5T", Numbers.format(5e12));
        assertEquals("1Qa", Numbers.format(1e15));
    }

    @Test
    void neverShowsOneThousandOfASuffix() {
        assertEquals("1M", Numbers.format(999_999));
        assertEquals("1B", Numbers.format(999_999_999));
    }

    @Test
    void hugeNumbersFallBackToScientific() {
        assertEquals("1Dc", Numbers.format(1e33));
        assertEquals("1E36", Numbers.format(1e36));
    }

    @Test
    void formatsFullNumbers() {
        assertEquals("1,234,567.89", Numbers.full(1234567.89));
        assertEquals("12,345", Numbers.whole(12345));
    }

    @Test
    void parsesPlayerInput() {
        assertEquals(1500, Numbers.parse("1500"));
        assertEquals(1500, Numbers.parse("1,500"));
        assertEquals(1500, Numbers.parse("1.5k"));
        assertEquals(2_000_000, Numbers.parse("2M"));
        assertEquals(3e9, Numbers.parse("3b"));
        assertEquals(1e15, Numbers.parse("1qa"));
        assertEquals(1_000_000, Numbers.parse("1e6"));
        assertEquals(250, Numbers.parse("$250"));
    }

    @Test
    void rejectsBadInput() {
        assertEquals(-1, Numbers.parse("abc"));
        assertEquals(-1, Numbers.parse("-5"));
        assertEquals(-1, Numbers.parse(""));
        assertEquals(-1, Numbers.parse("k"));
        assertEquals(-1, Numbers.parse("NaN"));
        assertEquals(-1, Numbers.parse("Infinity"));
        assertEquals(-1, Numbers.parseLong("1.5"));
        assertEquals(10, Numbers.parseLong("10"));
    }

    @Test
    void formatsTime() {
        assertEquals("0:05", Numbers.time(5));
        assertEquals("10:00", Numbers.time(600));
        assertEquals("1:01:01", Numbers.time(3661));
    }
}
