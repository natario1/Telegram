package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Property;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;

public class AvatarImageView extends BackupImageView {

    private final RectF rect = new RectF();
    private final Paint placeholderPaint;
    public boolean drawAvatar = true;
    public float bounceScale = 1f;

    private float crossfadeProgress;
    private ImageReceiver animateFromImageReceiver;

    private ImageReceiver foregroundImageReceiver;
    private float foregroundAlpha;
    private ImageReceiver.BitmapHolder drawableHolder;
    boolean drawForeground = true;
    float progressToExpand;

    ProfileGalleryView avatarsViewPager;
    private boolean hasStories;
    private float progressToInsets = 1f;

    public void setAvatarsViewPager(ProfileGalleryView avatarsViewPager) {
        this.avatarsViewPager = avatarsViewPager;
    }

    public AvatarImageView(Context context) {
        super(context);
        foregroundImageReceiver = new ImageReceiver(this);
        placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        placeholderPaint.setColor(Color.BLACK);
    }

    public void setAnimateFromImageReceiver(ImageReceiver imageReceiver) {
        this.animateFromImageReceiver = imageReceiver;
    }

    public void setCrossfadeProgress(float crossfadeProgress) {
        this.crossfadeProgress = crossfadeProgress;
        invalidate();
    }

    public static Property<AvatarImageView, Float> CROSSFADE_PROGRESS = new AnimationProperties.FloatProperty<AvatarImageView>("crossfadeProgress") {
        @Override
        public void setValue(AvatarImageView object, float value) {
            object.setCrossfadeProgress(value);
        }

        @Override
        public Float get(AvatarImageView object) {
            return object.crossfadeProgress;
        }
    };

    public void setForegroundImage(ImageLocation imageLocation, String imageFilter, Drawable thumb) {
        foregroundImageReceiver.setImage(imageLocation, imageFilter, thumb, 0, null, null, 0);
        if (drawableHolder != null) {
            drawableHolder.release();
            drawableHolder = null;
        }
    }

    public void setForegroundImageDrawable(ImageReceiver.BitmapHolder holder) {
        if (holder != null) {
            foregroundImageReceiver.setImageBitmap(holder.drawable);
        }
        if (drawableHolder != null) {
            drawableHolder.release();
            drawableHolder = null;
        }
        drawableHolder = holder;
    }

    public float getForegroundAlpha() {
        return foregroundAlpha;
    }

    public void setForegroundAlpha(float value) {
        foregroundAlpha = value;
        invalidate();
    }

    public void clearForeground() {
        AnimatedFileDrawable drawable = foregroundImageReceiver.getAnimation();
        if (drawable != null) {
            drawable.removeSecondParentView(this);
        }
        foregroundImageReceiver.clearImage();
        if (drawableHolder != null) {
            drawableHolder.release();
            drawableHolder = null;
        }
        foregroundAlpha = 0f;
        invalidate();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        foregroundImageReceiver.onDetachedFromWindow();
        if (drawableHolder != null) {
            drawableHolder.release();
            drawableHolder = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        foregroundImageReceiver.onAttachedToWindow();
    }

    @Override
    public void setRoundRadius(int value) {
        super.setRoundRadius(value);
        foregroundImageReceiver.setRoundRadius(value);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
        canvas.save();
        canvas.scale(bounceScale, bounceScale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
        float inset = hasStories ? (int) AndroidUtilities.dpf2(3.5f) : 0;
        inset *= (1f - progressToExpand);
        inset *= progressToInsets * (1f - foregroundAlpha);
        float alpha = 1.0f;
        if (animateFromImageReceiver != null) {
            alpha *= 1.0f - crossfadeProgress;
            if (crossfadeProgress > 0.0f) {
                final float fromAlpha = crossfadeProgress;
                final float wasImageX = animateFromImageReceiver.getImageX();
                final float wasImageY = animateFromImageReceiver.getImageY();
                final float wasImageW = animateFromImageReceiver.getImageWidth();
                final float wasImageH = animateFromImageReceiver.getImageHeight();
                final float wasAlpha = animateFromImageReceiver.getAlpha();
                animateFromImageReceiver.setImageCoords(inset, inset, getMeasuredWidth() - inset * 2f, getMeasuredHeight() - inset * 2f);
                animateFromImageReceiver.setAlpha(fromAlpha);
                animateFromImageReceiver.draw(canvas);
                animateFromImageReceiver.setImageCoords(wasImageX, wasImageY, wasImageW, wasImageH);
                animateFromImageReceiver.setAlpha(wasAlpha);
            }
        }
        if (imageReceiver != null && alpha > 0 && (foregroundAlpha < 1f || !drawForeground)) {
            imageReceiver.setImageCoords(inset, inset, getMeasuredWidth() - inset * 2f, getMeasuredHeight() - inset * 2f);
            final float wasAlpha = imageReceiver.getAlpha();
            imageReceiver.setAlpha(wasAlpha * alpha);
            if (drawAvatar) {
                imageReceiver.draw(canvas);
            }
            imageReceiver.setAlpha(wasAlpha);
        }
        if (foregroundAlpha > 0f && drawForeground && alpha > 0) {
            if (foregroundImageReceiver.getDrawable() != null) {
                foregroundImageReceiver.setImageCoords(inset, inset, getMeasuredWidth() - inset * 2f, getMeasuredHeight() - inset * 2f);
                foregroundImageReceiver.setAlpha(alpha * foregroundAlpha);
                foregroundImageReceiver.draw(canvas);
            } else {
                rect.set(0f, 0f, getMeasuredWidth(), getMeasuredHeight());
                placeholderPaint.setAlpha((int) (alpha * foregroundAlpha * 255f));
                final int radius = foregroundImageReceiver.getRoundRadius()[0];
                canvas.drawRoundRect(rect, radius, radius, placeholderPaint);
            }
        }
        canvas.restore();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (avatarsViewPager != null) {
            avatarsViewPager.invalidate();
        }
    }

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (progressToInsets == this.progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        //if (hasStories) {
        invalidate();
        //}
    }

    public void drawForeground(boolean drawForeground) {
        this.drawForeground = drawForeground;
    }

    public ChatActivityInterface getPrevFragment() {
        return null;
    }

    public void setHasStories(boolean hasStories) {
        if (this.hasStories == hasStories) {
            return;
        }
        this.hasStories = hasStories;
        invalidate();
    }

    public void setProgressToExpand(float animatedFracture) {
        if (progressToExpand == animatedFracture) {
            return;
        }
        progressToExpand = animatedFracture;
        invalidate();
    }
}
