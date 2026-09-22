package cn.net.rms.confluxmap.core.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MeasureFormatTest {
    @Test
    void dropsTrailingZeroBelowOneHundred() {
        assertEquals("0", MeasureFormat.blocks(0.0));
        assertEquals("8", MeasureFormat.blocks(8.0));
        assertEquals("3.4", MeasureFormat.blocks(3.44));
        assertEquals("99.5", MeasureFormat.blocks(99.46));
    }

    @Test
    void switchesToIntegerAtOneHundred() {
        assertEquals("100", MeasureFormat.blocks(99.96));
        assertEquals("123", MeasureFormat.blocks(123.4));
        assertEquals("4096", MeasureFormat.blocks(4096.49));
    }
}
