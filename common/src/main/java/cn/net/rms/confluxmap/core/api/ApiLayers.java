package cn.net.rms.confluxmap.core.api;

import cn.net.rms.confluxmap.api.MapDataApi.ApiLayer;
import cn.net.rms.confluxmap.core.model.MapLayer;

/**
 * Translation between the Minecraft-free {@link ApiLayer} taxonomy and the mod's
 * {@link MapLayer} model. Slice-style layers are not separately addressable through the
 * public API; {@code CAVE} resolves to the cave layer's current automatic slice.
 */
public final class ApiLayers {
    private ApiLayers() {
    }

    public static MapLayer toCore(final ApiLayer layer) {
        if (layer == null) {
            return MapLayer.SURFACE;
        }
        switch (layer) {
            case SURFACE: return MapLayer.SURFACE;
            case CAVE: return MapLayer.CAVE_AUTO;
            case NETHER: return MapLayer.NETHER_CURRENT;
            case NETHER_CEILING: return MapLayer.NETHER_CEILING;
            case END: return MapLayer.END_SURFACE;
            default: return MapLayer.SURFACE;
        }
    }

    public static ApiLayer fromCore(final MapLayer layer) {
        if (layer == null) {
            return ApiLayer.SURFACE;
        }
        switch (layer.type()) {
            case SURFACE: return ApiLayer.SURFACE;
            case CAVE_AUTO:
            case CAVE_SLICE: return ApiLayer.CAVE;
            case NETHER_CURRENT:
            case NETHER_SLICE: return ApiLayer.NETHER;
            case NETHER_CEILING: return ApiLayer.NETHER_CEILING;
            case END_SURFACE: return ApiLayer.END;
            default: return ApiLayer.SURFACE;
        }
    }
}
