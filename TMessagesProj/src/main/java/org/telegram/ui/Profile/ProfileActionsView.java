package org.telegram.ui.Profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import static org.telegram.messenger.AndroidUtilities.*;
import static org.telegram.messenger.Utilities.clamp;

public class ProfileActionsView extends LinearLayout {

    public enum Action {
        JOIN(R.string.VoipChatJoin, R.drawable.profile_actions_join);

        private final int textResId;
        private final int drawableResId;

        Action(@StringRes int textResId, @DrawableRes int drawableResId) {
            this.textResId = textResId;
            this.drawableResId = drawableResId;
        }
    }

    private final static float HORIZONTAL_GAP = dpf2(6.67F);
    private final static float HORIZONTAL_INSET = dpf2(12);
    private final static int ITEM_HEIGHT = dp(54);
    private final static float ITEM_PADDING_X = HORIZONTAL_GAP / 2F;
    private final static float ITEM_PADDING_TOP = dpf2(10.33F);
    private final static float ITEM_PADDING_BOTTOM = dpf2(9.67F);
    private final static int PADDING_Y = dp(12);
    private final static float ICON_SIZE = dpf2(19.33F);
    private final static float TEXT_SIZE = dpf2(10.9F);

    private final Button[] buttons = new Button[4];

    public ProfileActionsView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        int px = (int) (HORIZONTAL_INSET - ITEM_PADDING_X);
        setPadding(px, PADDING_Y, px, PADDING_Y);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new Button(context, null);
            addView(buttons[i]);
        }
        buttons[0].setAction(Action.JOIN);
        buttons[1].setAction(Action.JOIN);
        buttons[2].setAction(Action.JOIN);
        buttons[3].setAction(Action.JOIN);
        setVisibleRoom(0F);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1F);
    }

    public void setVisibleRoom(float room) {
        float height = ITEM_HEIGHT + PADDING_Y * 2;
        float p0 = 0.1F * height;
        float p1 = 1.1F * height;
        float progress = clamp((room - p0) / (p1 - p0), 1, 0);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setProgress(progress);
        }
        setVisibility(progress > 0F ? View.VISIBLE : View.GONE);
    }

    private static class Button extends View {
        private final ViewClipper clipper = new ViewClipper(this);
        private final Paint fallbackPaint = new Paint();
        private final BlurBehindDrawable blurDrawable;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";
        private Drawable icon;
        private float iconScale = 1F;

        private Button(Context context, Action data) {
            super(context);
            clipper.setRoundRadius(dpf2(8.33F));
            fallbackPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextAlign(Paint.Align.CENTER);
            // Seems not worth it for performance - we are already drawing on a blurred bottom bar.
            /* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.canBlurChat() && SharedConfig.useNewBlur) {
                blurDrawable = new BlurBehindDrawable(this);
                blurDrawable.behindViews.add(behind);
            } else {
                blurDrawable = null;
            } */
            blurDrawable = null;
            setWillNotDraw(false);
            setAction(data);
            setProgress(0F);
        }

        private void setAction(Action data) {
            if (data != null) {
                this.text = LocaleController.getString(data.textResId);
                this.icon = ContextCompat.getDrawable(getContext(), data.drawableResId);
                this.icon.setAlpha(textPaint.getAlpha());
                setVisibility(View.VISIBLE);
                invalidate();
            } else {
                this.text = "";
                this.icon = null;
                setVisibility(View.GONE);
            }
        }

        private void setProgress(float progress) {
            int alpha = (int) (0xFF * Math.max(0F, (progress - 0.4F) / 0.6F));
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(lerp(0.6F * TEXT_SIZE, TEXT_SIZE, progress));
            if (icon != null) icon.setAlpha(alpha);
            iconScale = Math.max(0F, lerp(-0.6F, 1F, progress));
            int bg = lerp(255, 218, progress);
            fallbackPaint.setColor(Color.rgb(bg, bg, bg));
            clipper.setInsets(ITEM_PADDING_X, lerp(ITEM_HEIGHT, 0, progress), ITEM_PADDING_X, 0);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(ITEM_HEIGHT, MeasureSpec.EXACTLY));
        }

        @SuppressLint("NewApi")
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            clipper.apply(canvas);
            if (blurDrawable != null && canvas.isHardwareAccelerated()) {
                blurDrawable.setBounds(0, 0, getWidth(), getHeight());
                blurDrawable.refresh(0, 0);
                blurDrawable.draw(canvas);
            } else {
                canvas.drawRect(0F, 0F, getWidth(), getHeight(), fallbackPaint);
            }
            float cx = getWidth() / 2F;
            float is = iconScale * ICON_SIZE / 2F;
            float it = ITEM_PADDING_TOP + clipper.getBoundingBox().top;
            icon.setBounds((int) (cx - is), (int) it, (int) (cx + is), (int) (it + is*2));
            canvas.drawText(text, cx, getHeight() - ITEM_PADDING_BOTTOM, textPaint);
            icon.draw(canvas);
            canvas.restore();
        }
    }
}
