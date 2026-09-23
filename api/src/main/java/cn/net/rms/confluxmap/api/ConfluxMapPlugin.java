package cn.net.rms.confluxmap.api;

/**
 * Entry point for Conflux Map integration plugins. Declare an implementation under the
 * {@code "confluxmap"} key in your {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "confluxmap": [ "com.example.ExampleConfluxMapPlugin" ]
 * }
 * }</pre>
 *
 * The mod invokes every declared plugin on the client main thread once its services are
 * fully started. A throwing plugin is logged and skipped; other plugins still load.
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface ConfluxMapPlugin {
    void onConfluxMapInitialize(ConfluxMapApi api);
}
