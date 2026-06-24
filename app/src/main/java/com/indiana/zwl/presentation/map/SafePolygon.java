package com.indiana.zwl.presentation.map;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.layer.overlay.Polygon;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.GraphicFactory;

public abstract class SafePolygon extends Polygon {
    public SafePolygon(Paint fillPaint, Paint strokePaint, GraphicFactory graphicFactory) {
        super(fillPaint, strokePaint, graphicFactory);
    }

    @Override
    public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
        return safeOnTap(tapLatLong, layerXY, tapXY);
    }

    public abstract boolean safeOnTap(LatLong tapLatLong, Point layerXY, Point tapXY);

    public boolean parentOnTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
        return super.onTap(tapLatLong, layerXY, tapXY);
    }
}
