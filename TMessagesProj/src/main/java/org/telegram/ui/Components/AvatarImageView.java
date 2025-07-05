package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.Property;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.Utilities;

public class AvatarImageView extends BackupImageView {

    public boolean drawAvatar = true;
    public float bounceScale = 1f;

    private final RectF rect = new RectF();
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float crossfadeProgress;
    private ImageReceiver crossfadeImageReceiver;

    private final ImageReceiver foregroundImageReceiver;
    private float foregroundAlpha;
    private ImageReceiver.BitmapHolder foregroundHolder;
    private boolean drawForeground = true;

    private final ImageReceiver blurImageReceiver;
    private float blurAlpha;
    private ImageReceiver.BitmapHolder blurHolder;

    float progressToExpand;

    private boolean hasStories;
    private float progressToInsets = 1f;


    public AvatarImageView(Context context) {
        super(context);
        foregroundImageReceiver = new ImageReceiver(this);
        blurImageReceiver = new ImageReceiver(this);
        dimPaint.setColor(Color.BLACK);
    }

    public void setCrossfadeImage(ImageReceiver imageReceiver) {
        this.crossfadeImageReceiver = imageReceiver;
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
        if (foregroundHolder != null) {
            foregroundHolder.release();
            foregroundHolder = null;
        }
    }

    public void setForegroundImage(ImageReceiver.BitmapHolder holder) {
        if (holder != null) {
            if (holder.drawable != null) {
                foregroundImageReceiver.setImageBitmap(holder.drawable);
            } else {
                foregroundImageReceiver.setImageBitmap(holder.bitmap);
            }
        }
        if (foregroundHolder != null) foregroundHolder.release();
        foregroundHolder = holder;
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
        if (foregroundHolder != null) {
            foregroundHolder.release();
            foregroundHolder = null;
        }
        foregroundAlpha = 0f;
        invalidate();
    }

    @Override
    public void onNewImageSet() {
        super.onNewImageSet();
        if (blurHolder != null) {
            blurHolder.release();
            blurHolder = null;
        }
        Bitmap bitmap = imageReceiver.getBitmap();
        if (bitmap != null && !bitmap.isRecycled()) {
            Bitmap blur = Utilities.stackBlurBitmapMax(bitmap, true);
            blurImageReceiver.setImageBitmap(blur);
            blurHolder = new ImageReceiver.BitmapHolder(blur);
        }
    }

    public void setBlurAlpha(float value) {
        blurAlpha = value;
        invalidate();
    }

    public void setDimAlpha(float value) {
        dimPaint.setAlpha((int) (value * 0xFF));
        invalidate();
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        foregroundImageReceiver.onDetachedFromWindow();
        blurImageReceiver.onDetachedFromWindow();
        if (foregroundHolder != null) foregroundHolder.release();
        if (blurHolder != null) blurHolder.release();
        blurHolder = null;
        foregroundHolder = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        foregroundImageReceiver.onAttachedToWindow();
        blurImageReceiver.onAttachedToWindow();
    }

    @Override
    public void setRoundRadius(int value) {
        super.setRoundRadius(value);
        foregroundImageReceiver.setRoundRadius(value);
        blurImageReceiver.setRoundRadius(value);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (animatedEmojiDrawable != null && animatedEmojiDrawable.getImageReceiver() != null) {
            animatedEmojiDrawable.getImageReceiver().startAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
        canvas.save();
        canvas.scale(bounceScale, bounceScale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
        float inset = hasStories ? (int) AndroidUtilities.dpf2(5.33f) : 0;
        inset *= (1f - progressToExpand);
        inset *= progressToInsets * (1f - foregroundAlpha);
        rect.set(0F, 0F, getWidth(), getHeight());
        rect.inset(inset, inset);
        float alpha = 1.0f;
        if (crossfadeImageReceiver != null) {
            alpha *= 1.0f - crossfadeProgress;
            if (crossfadeProgress > 0.0f) {
                final float fromAlpha = crossfadeProgress;
                final float wasImageX = crossfadeImageReceiver.getImageX();
                final float wasImageY = crossfadeImageReceiver.getImageY();
                final float wasImageW = crossfadeImageReceiver.getImageWidth();
                final float wasImageH = crossfadeImageReceiver.getImageHeight();
                final float wasAlpha = crossfadeImageReceiver.getAlpha();
                crossfadeImageReceiver.setImageCoords(rect);
                crossfadeImageReceiver.setAlpha(fromAlpha);
                crossfadeImageReceiver.draw(canvas);
                crossfadeImageReceiver.setImageCoords(wasImageX, wasImageY, wasImageW, wasImageH);
                crossfadeImageReceiver.setAlpha(wasAlpha);
            }
        }
        float foregroundAlpha = drawForeground && foregroundImageReceiver.getDrawable() != null ? this.foregroundAlpha*alpha : 0F;
        float blurAlpha = blurImageReceiver.getDrawable() != null ? this.blurAlpha : 0F;
        if (imageReceiver != null && alpha > 0 && drawAvatar && foregroundAlpha < 1F && blurAlpha < 1F && dimPaint.getAlpha() < 0xFF) {
            imageReceiver.setImageCoords(rect);
            final float wasAlpha = imageReceiver.getAlpha();
            imageReceiver.setAlpha(wasAlpha * alpha);
            imageReceiver.draw(canvas);
            imageReceiver.setAlpha(wasAlpha);
        }
        if (foregroundAlpha > 0F && blurAlpha < 1F && dimPaint.getAlpha() < 0xFF) {
            foregroundImageReceiver.setImageCoords(rect);
            foregroundImageReceiver.setAlpha(foregroundAlpha);
            foregroundImageReceiver.draw(canvas);
        }
        if (blurAlpha > 0F && dimPaint.getAlpha() < 0xFF) {
            blurImageReceiver.setImageCoords(rect);
            blurImageReceiver.setAlpha(blurAlpha);
            blurImageReceiver.draw(canvas);
        }
        if (dimPaint.getAlpha() > 0) {
            final int radius = foregroundImageReceiver.getRoundRadius()[0];
            canvas.drawRoundRect(rect, radius, radius, dimPaint);

        }
        canvas.restore();
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
