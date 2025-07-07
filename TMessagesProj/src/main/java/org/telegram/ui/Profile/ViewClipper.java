package org.telegram.ui.Profile;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import androidx.annotation.NonNull;


/**
 * Applies basic View transformation at the canvas level.
 * Custom views can expose a public clipper to let parents manage complex animations.
 */
class ViewClipper {
    public float roundRadius = 0F;
    private float insetTop = 0F;
    private float insetLeft = 0F;
    private float insetBottom = 0F;
    private float insetRight = 0F;
    private Path customPath = null;
    private float customPathX = 0;
    private float customPathY = 0;
    private boolean dirty = false;
    private final View view;
    private final Path path = new Path();
    private final RectF rect = new RectF();


    public ViewClipper(@NonNull View view) {
        this.view = view;
    }

    public void setRoundRadius(float roundRadius) {
        if (roundRadius == this.roundRadius) return;
        this.roundRadius = roundRadius;
        this.dirty = true;
        view.invalidate();
    }

    public void setInset(float inset) {
        setInsets(inset, inset, inset, inset);
    }

    public void setInsets(float left, float top, float right, float bottom) {
        if (left == this.insetLeft && top == this.insetTop && right == this.insetRight && bottom == this.insetBottom) return;
        insetLeft = left;
        insetTop = top;
        insetRight = right;
        insetBottom = bottom;
        dirty = true;
        view.invalidate();
    }

    public RectF getBoundingBox() {
        rect.set(insetLeft, insetTop, view.getWidth() - insetRight, view.getHeight() - insetBottom);
        return rect;
    }

    public void setCustom(Path path, float pathX, float pathY, boolean invalidate) {
        if (path == null && this.customPath == null) return;
        this.customPath = path;
        this.customPathX = pathX;
        this.customPathY = pathY;
        if (invalidate) view.invalidate();
    }

    // Call whenever view scale or size change
    public void invalidate() {
        dirty = true;
        view.invalidate();
    }

    public boolean needsApply() {
        return insetLeft != 0 || insetRight != 0 || insetTop != 0 || insetBottom != 0 || customPath != null || roundRadius != 0;
    }

    public void apply(@NonNull Canvas canvas) {
        if (customPath != null) {
            canvas.translate(-customPathX, -customPathY);
            canvas.clipPath(customPath);
            canvas.translate(customPathX, customPathY);
        } else {
            if (dirty) {
                path.rewind();
                getBoundingBox();
                // Visible round radius will be affected by scale, we need to compensate.
                float radius = Math.min(roundRadius, Math.min(rect.width()*view.getScaleX(), rect.height()*view.getScaleY()) / 2f);
                path.addRoundRect(rect, radius / view.getScaleX(), radius / view.getScaleY(), Path.Direction.CW);
                dirty = false;
            }
            canvas.clipPath(path);
        }
    }
}
