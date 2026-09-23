package cn.net.rms.confluxmap.api;

import java.util.Objects;

/**
 * Handle returned by {@link Event#register}. Closing it unregisters the listener;
 * closing twice is a no-op. Implementations should hold the handle for the lifetime of
 * their integration and close it when their mod disables.
 */
public final class EventRegistration implements AutoCloseable {
    private final Runnable unregister;
    private boolean closed;

    EventRegistration(final Runnable unregister) {
        this.unregister = Objects.requireNonNull(unregister, "unregister");
    }

    /** Wraps an arbitrary unregistration action; used by mod-side provider registries. */
    @ApiStatus.Internal
    public static EventRegistration of(final Runnable unregister) {
        return new EventRegistration(unregister);
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            unregister.run();
        }
    }
}
