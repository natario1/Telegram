package org.telegram.ui.Profile;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the blurred version of views that intersect the host view, accounting for translation
 * and scale in any ancestor view.
 */
@RequiresApi(Build.VERSION_CODES.S)
class IntersectionBlurDrawable extends Drawable {

    private final View host;
    private final RenderNode node = new RenderNode(null);
    private final RectF hostLocation = new RectF();
    private final RectF behindLocation = new RectF();
    private final RectF intersectLocation = new RectF();
    private final float[] hostScale = new float[2];
    private final float[] behindScale = new float[2];

    private float extraX = 0F;
    private float extraY = 0F;

    private final List<View> behindViews;

    IntersectionBlurDrawable(View host, View... behind) {
        this.host = host;
        setCallback(host);
        behindViews = Arrays.asList(behind);
        node.setRenderEffect(RenderEffect.createBlurEffect(60, 60, Shader.TileMode.CLAMP));
    }

    void setOffset(float extraX, float extraY) {
        this.extraX = extraX;
        this.extraY = extraY;
    }

    private void findRect(View view, RectF rect, float[] scale, float extraX, float extraY) {
        rect.set(0, 0, view.getWidth(), view.getHeight());
        rect.offset(extraX, extraY);
        scale[0] = scale[1] = 1F;
        view.getMatrix().mapRect(rect);
        scale[0] *= view.getScaleX();
        scale[1] *= view.getScaleY();
        rect.offset(view.getLeft(), view.getTop());
        ViewParent vp = view.getParent();
        while (vp instanceof View) {
            final View parent = (View) vp;
            rect.offset(-parent.getScrollX(), -parent.getScrollY());
            parent.getMatrix().mapRect(rect);
            rect.offset(parent.getLeft(), parent.getTop());
            scale[0] *= parent.getScaleX();
            scale[1] *= parent.getScaleY();
            vp = parent.getParent();
        }
    }

    void refresh() {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || behindViews.isEmpty()) return;
        findRect(host, hostLocation, hostScale, extraX, extraY);
        RecordingCanvas canvas = node.beginRecording(bounds.width(), bounds.height());
        for (View view : behindViews) {
            findRect(view, behindLocation, behindScale, 0, 0);
            if (!intersectLocation.setIntersect(hostLocation, behindLocation)) continue;
            canvas.save();
            canvas.scale(1F/hostScale[0], 1F/hostScale[1]); // undo global host scale
            canvas.translate(intersectLocation.left-hostLocation.left, intersectLocation.top-hostLocation.top); // move to intersection origin
            canvas.scale(behindScale[0], behindScale[1], 0, 0); // apply global view scale
            canvas.translate((behindLocation.left-intersectLocation.left)/behindScale[0], (behindLocation.top-intersectLocation.top)/behindScale[1]); // translate to view origin
            canvas.translate(-view.getScrollX(), -view.getScrollY());
            view.draw(canvas);
            canvas.restore();
        }
        node.endRecording();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (canvas.isHardwareAccelerated()) {
            Rect bounds = getBounds();
            node.setPosition(bounds.left, bounds.top, bounds.right, bounds.bottom);
            canvas.drawRenderNode(node);
        }
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
