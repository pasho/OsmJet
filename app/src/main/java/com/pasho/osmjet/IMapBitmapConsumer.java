package com.pasho.osmjet;

import android.graphics.Bitmap;

/**
 * Created by Pavel on 26/06/2016.
 */
public interface IMapBitmapConsumer {
    void onMapBitmap(Bitmap bitmap);
    void onViewerPosition(int[] viewerPosition);
}
