package com.pasho.osmmap;

import android.graphics.Bitmap;

/**
 * Created by Pavel on 26/06/2016.
 */
public interface ITilesConsumer {
    void onTiles(Bitmap[] bitmaps);
}
