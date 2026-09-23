package cn.net.rms.confluxmap.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A copy-on-write listener list. Registration is safe from any thread; the mod fires
 * events on the client main thread unless a method documents otherwise, and listeners
 * must not throw - one throwing listener would abort the remaining notifications of that
 * event.
 *
 * @param <T> immutable event payload type
 */
public final class Event<T> {
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private List<Consumer<T>> snapshot = List.of();

    /**
     * Registers a listener and returns a handle whose {@link EventRegistration#close()}
     * unregisters it. The handle is {@link AutoCloseable}; closing it twice is a no-op.
     */
    public EventRegistration register(final Consumer<T> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            listeners.add(listener);
            snapshot = List.copyOf(listeners);
        }
        final class Removal implements Runnable {
            @Override
            public void run() {
                synchronized (Event.this) {
                    listeners.remove(listener);
                    snapshot = List.copyOf(listeners);
                }
            }
        }
        return new EventRegistration(new Removal());
    }

    /** Whether at least one listener is currently registered. */
    public synchronized boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /** Notifies every listener registered at call time, in registration order. */
    public void fire(final T event) {
        final List<Consumer<T>> current;
        synchronized (this) {
            current = snapshot;
        }
        for (final Consumer<T> listener : current) {
            listener.accept(event);
        }
    }
}
