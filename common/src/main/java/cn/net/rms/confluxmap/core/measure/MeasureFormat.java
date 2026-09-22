package cn.net.rms.confluxmap.core.measure;

import java.util.Locale;

/** Compact block-distance text: one decimal below 100, integer above. */
public final class MeasureFormat {
    private MeasureFormat() {
    }

    public static String blocks(final double distance) {
        if (distance < 100.0) {
            final String rounded = String.format(Locale.ROOT, "%.1f", distance);
            return rounded.endsWith(".0")
                ? rounded.substring(0, rounded.length() - 2)
                : rounded;
        }
        return Long.toString(Math.round(distance));
    }
}
