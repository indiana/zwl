package com.indiana.zwl.presentation.map;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.layer.Layer;

public abstract class SafeLayer extends Layer {
    @Override
    public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
        return safeOnTap(tapLatLong, layerXY, tapXY);
    }

    public abstract boolean safeOnTap(LatLong tapLatLong, Point layerXY, Point tapXY);

    public boolean parentOnTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
        return super.onTap(tapLatLong, layerXY, tapXY);
    }
}
