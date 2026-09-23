package cn.net.rms.confluxmap.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stability markers for the Conflux Map public API. The mod carries its own annotations
 * instead of a third-party annotations dependency so the published api artifact stays
 * dependency-free.
 */
public final class ApiStatus {
    private ApiStatus() {
    }

    /**
     * The element may change or be removed in any release while the API is experimental.
     * Consumers should expect breaking changes between minor versions until the API
     * reaches its first stable declaration.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
    public @interface Experimental {
    }

    /** The element belongs to the mod's bootstrap and is not part of the public contract. */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface Internal {
    }
}
