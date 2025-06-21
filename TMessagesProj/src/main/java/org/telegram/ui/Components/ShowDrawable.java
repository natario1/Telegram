package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;

import static org.telegram.messenger.AndroidUtilities.dp;

public class ShowDrawable extends Drawable implements SimpleTextView.PressableDrawable {

    public final AnimatedTextView.AnimatedTextDrawable textDrawable;
    public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ShowDrawable(String string) {
        textDrawable = new AnimatedTextView.AnimatedTextDrawable();
        textDrawable.setCallback(new Callback() {
            @Override
            public void invalidateDrawable(@NonNull Drawable who) {
                if (view != null) {
                    view.invalidate();
                }
            }

            @Override
            public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            }

            @Override
            public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            }
        });
        textDrawable.setText(string);
        textDrawable.setTextSize(dp(11));
        textDrawable.setGravity(Gravity.CENTER);
        backgroundPaint.setColor(0x1f000000);
    }

    private int textColor;

    public void setBackgroundColor(int backgroundColor) {
        if (backgroundPaint.getColor() != backgroundColor) {
            backgroundPaint.setColor(backgroundColor);
            invalidateSelf();
        }
    }

    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            this.textColor = textColor;
            invalidateSelf();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float alpha = this.alpha * this.alpha2;
        if (alpha <= 0) return;
        AndroidUtilities.rectTmp.set(getBounds());
        canvas.save();
        final float s = bounce.getScale(0.1f);
        canvas.scale(s, s, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
        final int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (wasAlpha * alpha));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(20), dp(20), backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);
        textDrawable.setTextColor(textColor);
        textDrawable.setAlpha((int) (0xFF * alpha));
        textDrawable.setBounds((int) AndroidUtilities.rectTmp.left, (int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, (int) AndroidUtilities.rectTmp.bottom);
        textDrawable.draw(canvas);
        canvas.restore();
    }

    private float alpha = 1f, alpha2 = 1f;

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha / 255f;
        invalidateSelf();
    }

    public void setAlpha2(float alpha) {
        this.alpha2 = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (textDrawable.getAnimateToWidth() + dp(11));
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(17.33f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    private boolean pressed;
    private final ButtonBounce bounce = new ButtonBounce(null) {
        @Override
        public void invalidate() {
            invalidateSelf();
        }
    };

    @Override
    public void setPressed(boolean pressed) {
        bounce.setPressed(pressed);
        this.pressed = pressed;
    }

    @Override
    public boolean isPressed() {
        return pressed;
    }

    private View view;

    public void setView(View view) {
        this.view = view;
    }
}
