package org.telegram.ui.Profile;

import android.graphics.*;
import android.graphics.Point;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.*;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.Stars.StarGiftPatterns;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

public class ProfileHeaderView extends ProfileCoordinatorLayout.Header {

    private final ActionBar actionBar;
    private final Theme.ResourcesProvider resourcesProvider;
    private float actionModeProgress = 0F;
    // Used for QR menu item display too
    private final int unit = dp(72);
    private final Rect blurBounds = new Rect();
    private final SizeNotifierFrameLayout root;

    private final AnimatedFloat hasThemeAnimated = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedColor themeColor1Animated = new AnimatedColor(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedColor themeColor2Animated = new AnimatedColor(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean hasTheme;
    private int themeColor1;
    private int themeColor2;

    private final Paint plainPaint = new Paint();
    private int plainColor;

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient gradient;
    private int gradientArg;
    private final Matrix gradientMatrix = new Matrix();

    private final AnimatedFloat emojiLoadedT = new AnimatedFloat(this, 0, 440, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat emojiFullT = new AnimatedFloat(this, 0, 440, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(20), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
    private int emojiColor;
    private boolean hasEmoji;
    private boolean isEmojiLoaded;
    private boolean isEmojiCollectible;


    public ProfileHeaderView(
            SizeNotifierFrameLayout root,
            @NonNull ActionBar actionBar,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(actionBar.getContext());
        this.root = root;
        this.actionBar = actionBar;
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        setBackgroundColor(getThemedColor(Theme.key_avatar_backgroundActionBarBlue));
    }

    public void onConfigurationChanged(Point size) {
        int baseHeight = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        int overscrollHeight = dp(48);
        int availableHeight = size.y - baseHeight;

        // Configure base height
        configureHeights(baseHeight);

        // Configure growth
        // WIP: also !isTablet and avatarImage.getImageReceiver().hasNotThumb()
        boolean landscape = size.x > size.y;
        boolean expandable = !landscape && !AndroidUtilities.isAccessibilityScreenReaderEnabled();
        if ((expandable && snapGrowths.length == 3) || (!expandable && snapGrowths.length == 2)) return;
        int mid = (int) Math.min(0.7F * availableHeight, dp(246));
        if (expandable) {
            int max = Math.min(availableHeight - overscrollHeight, dp(398));
            configureGrowth(max + overscrollHeight, new int[]{0, mid, max});
        } else {
            configureGrowth(mid, new int[]{0, mid});
        }

        // Animate to mid value
        changeGrowth(mid, true);
    }

    @Override
    protected int onContentTouch(int dy) {
        if (growth <= snapGrowths[1] || snapGrowths.length < 3) return dy;
        if (dy > 0) return dy; // no resistance when shrinking
        float progress = ((float) (growth - snapGrowths[1])) / (snapGrowths[2] - snapGrowths[1]);
        float factor;
        if (progress < 0.25F) { // slow down
            float t = progress / 0.25F;
            factor = AndroidUtilities.lerp(0.6F, 0.2F, t);
        } else { // accelerate
            float t = (progress - 0.25F) / 0.75F;
            factor = AndroidUtilities.lerp(4F, 0.6F, t);
        }
        return Math.round(dy * factor);
    }

    @Override
    protected void onGrowthChanged(int growth) {
        super.onGrowthChanged(growth);
        invalidate();
    }

    // THEME & COLORS

    @Override
    public void setBackgroundColor(int color) {
        if (color != plainColor) {
            plainColor = color;
            invalidate();
        }
    }

    public void getThemeDescriptions(ArrayList<ThemeDescription> arrayList) {
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public int getAverageColor() {
        return hasTheme ? ColorUtils.blendARGB(themeColor1, themeColor2, 0.25f) : plainColor;
    }

    public void updateColors(MessagesController.PeerColor peerColor, boolean animated) {
        if (peerColor != null) {
            hasTheme = true;
            themeColor1 = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            themeColor2 = peerColor.getStoryColor1(Theme.isCurrentThemeDark());
            if (peerColor.patternColor != 0) {
                emojiColor = peerColor.patternColor;
            } else {
                emojiColor = PeerColorActivity.adaptProfileEmojiColor(themeColor1);
            }
        } else {
            if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) > .8f) {
                emojiColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText);
            } else if (AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_actionBarDefault)) < .2f) {
                emojiColor = Theme.multAlpha(getThemedColor(Theme.key_actionBarDefaultTitle), .5f);
            } else {
                emojiColor = PeerColorActivity.adaptProfileEmojiColor(getThemedColor(Theme.key_actionBarDefault));
            }
        }
        hasTheme = peerColor != null;
        getAverageColor();
        if (!animated) {
            themeColor1Animated.set(themeColor1, true);
            themeColor2Animated.set(themeColor2, true);
        }
        invalidate();
    }

    // EMOJI

    public void setBackgroundEmojiId(long emojiId, boolean isCollectible, boolean animated) {
        emoji.set(emojiId, animated);
        emoji.setColor(emojiColor);
        isEmojiCollectible = isCollectible;
        if (!animated) emojiFullT.force(isCollectible);
        hasEmoji = hasEmoji || emojiId != 0 && emojiId != -1;
        invalidate();
    }

    private boolean isEmojiLoaded() {
        if (!isEmojiLoaded && emoji != null && emoji.getDrawable() instanceof AnimatedEmojiDrawable) {
            AnimatedEmojiDrawable drawable = (AnimatedEmojiDrawable) emoji.getDrawable();
            isEmojiLoaded = drawable.getImageReceiver() != null && drawable.getImageReceiver().hasImageLoaded();
        }
        return isEmojiLoaded;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        emoji.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        emoji.detach();
    }


    // CANVAS

    public void setActionModeProgress(float progress) {
        actionModeProgress = progress;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float hidden = -getTranslationY();
        canvas.translate(0, hidden);
        int visible = getMeasuredHeight() - (int) hidden; // 'v'
        int paintable = (int) (visible * (1.0f - actionModeProgress));  // y1
        int width = getMeasuredWidth();

        if (paintable != 0) {
            // Set up plain and gradient paints
            plainPaint.setAlpha(255);
            plainPaint.setColor(plainColor);
            int themeBackground = themeColor1Animated.set(this.themeColor1);
            int themeOverlay = themeColor2Animated.set(this.themeColor2);
            if (gradient == null || gradientArg != themeOverlay) {
                gradientArg = themeOverlay;
                gradient = new RadialGradient(0.5F, 0.5F, 0.5F, themeOverlay, ColorUtils.setAlphaComponent(themeOverlay, 0), Shader.TileMode.CLAMP);
                gradientPaint.setShader(gradient);
            }

            // Blend them based on progress
            // WIP: progress factor is (playProfileAnimation == 0 ? 1f : avatarAnimationProgress)
            final float progress = hasThemeAnimated.set(hasTheme);
            if (progress < 1) {
                canvas.drawRect(0, 0, width, paintable, plainPaint);
            }
            if (progress > 0) {
                plainPaint.setAlpha((int) (0xFF * progress));
                plainPaint.setColor(themeBackground);
                canvas.drawRect(0, 0, width, paintable, plainPaint);

                // Gradient: as the header collapses, it translates, shrinks and fades
                float p = Math.min(1F, (float) growth / snapGrowths[1]);
                float shrink = lerp(0.6F, 1F, p);
                float centerY = lerp(0F, paintable / 2F, p);
                float alpha = lerp(0.2F, 0.6F, p);
                float size = Math.min(width - dp(60), dp(398));
                gradientMatrix.setScale(size, size);
                gradientMatrix.postTranslate((width - size) / 2F, centerY - size / 2F);
                gradientMatrix.postScale(shrink, shrink, width / 2F, centerY);
                gradient.setLocalMatrix(gradientMatrix);
                gradientPaint.setAlpha((int) (0xFF * progress * alpha));
                canvas.drawRect(0, 0, width, paintable, gradientPaint);
            }

            if (hasEmoji && emojiLoadedT.set(isEmojiLoaded()) > 0) {
                canvas.save();
                canvas.clipRect(0, 0, width, paintable);
                StarGiftPatterns.drawProfilePattern(
                        canvas,
                        emoji,
                        width,
                        dp(142), // WIP: proper value
                        Math.min(1f, (float) growth / unit),
                        emojiFullT.set(isEmojiCollectible)
                );
                canvas.restore();
            }
        }

        if (paintable != visible) {
            int color = getThemedColor(Theme.key_windowBackgroundWhite);
            plainPaint.setColor(color);
            blurBounds.set(0, paintable, width, visible);
            root.drawBlurRect(canvas, getY(), blurBounds, plainPaint, true);
        }
        /*WIP: if (parentLayout != null) {
            parentLayout.drawHeaderShadow(canvas, (int) (headerShadowAlpha * 255), (int) visible);
        } */

        canvas.restore();
    }
}
