package org.telegram.ui.Profile;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(Build.VERSION_CODES.S)
class BlurBehindDrawable extends Drawable {

    private final View host;
    private final RenderNode node = new RenderNode(null);
    private final int[] hostLocation = new int[2];
    private final int[] behindLocation = new int[2];

    List<View> behindViews = new ArrayList<>();

    BlurBehindDrawable(View host) {
        this.host = host;
        setCallback(host);
        node.setRenderEffect(RenderEffect.createBlurEffect(60, 60, Shader.TileMode.CLAMP));
    }

    void refresh(int deltaX, int deltaY) {
        if (getBounds().isEmpty() || behindViews.isEmpty()) return;

        Rect bounds = getBounds();
        node.setPosition(bounds.left, bounds.top, bounds.right, bounds.bottom);
        host.getLocationInWindow(hostLocation);
        RecordingCanvas canvas = node.beginRecording();
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (View view : behindViews) {
            if (view.getVisibility() != View.VISIBLE) continue;
            view.getLocationInWindow(behindLocation);
            float offsetX = behindLocation[0] - hostLocation[0] + deltaX - view.getScrollX();
            float offsetY = behindLocation[1] - hostLocation[1] + deltaY - view.getScrollY();
            canvas.save();
            canvas.translate(offsetX, offsetY);
            canvas.concat(view.getMatrix());
            view.draw(canvas);
            canvas.restore();
        }
        node.endRecording();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (canvas.isHardwareAccelerated()) {
            canvas.drawRenderNode(node);
        }
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
