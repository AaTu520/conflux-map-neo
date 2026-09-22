package cn.net.rms.confluxmap.compat;

//#if MC>=260300
//$$ import com.mojang.blaze3d.platform.InputConstants;
//#endif

/**
 * Mouse button codes compared against raw screen mouse events.
 *
 * <p>Through 26.2 these are GLFW button indices (left 0, right 1). 26.3 replaced GLFW with SDL
 * and moved the codes onto {@code InputConstants} with SDL values (left 1, middle 2, right 3),
 * so literal {@code button == 0} / {@code button == 1} checks silently swap or drop buttons on
 * 26.3. Compare against these constants instead; they are compile-time ints on every version.
 */
public final class MouseButtons {
    //#if MC>=260300
    //$$ public static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    //$$ public static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    //#else
    public static final int LEFT = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
    public static final int RIGHT = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    //#endif

    private MouseButtons() {
    }
}
