package org.telegram.ui.Profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stars.StarGiftPatterns;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.ProfileStoriesView;
import org.telegram.ui.Stories.StoryViewer;

import java.util.*;
import java.util.stream.IntStream;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static org.telegram.messenger.AndroidUtilities.*;
import static org.telegram.messenger.Utilities.clamp;
import static org.telegram.ui.Components.LayoutHelper.MATCH_PARENT;
import static org.telegram.ui.Components.LayoutHelper.createFrame;
import static org.telegram.ui.Stars.StarsController.findAttribute;

public class ProfileHeaderView extends ProfileCoordinatorLayout.Header implements NotificationCenter.NotificationCenterDelegate {

    private final static float GIFT_LAYOUT_INSET = 1.37F; // makes room for the radial background
    private final static float[][] GIFTS_LAYOUT = new float[][]{
            // horizontal
            { 108.76F, 0.0766F, GIFT_LAYOUT_INSET * 25.3F, .9F },
            { 103.01F, -3.1287F, GIFT_LAYOUT_INSET * 25.3F, .9F },
            // bottom
            { 78.48F, -2.6779F, GIFT_LAYOUT_INSET * 25.3F, .6F },
            { 79.43F, -0.3653F, GIFT_LAYOUT_INSET * 25.3F, 0 },
            // top
            { 76.54F, 0.4711F, GIFT_LAYOUT_INSET * 25.3F, .6F },
            { 76.84F, 2.5831F, GIFT_LAYOUT_INSET * 25.3F, 0 }
    };

    private final static int AVATAR_SIZE = dp(90);
    private final static int AVATAR_BOTTOM_PADDING = dp(140);

    private final static int ATTRACTOR_HIDDEN_Y = dp(16);
    private final static float FULLSCREEN_EXPAND_TRIGGER = .25F;
    private final static float FULLSCREEN_COLLAPSE_TRIGGER = .85F;

    private final static int HEIGHT_MID = dp(270);
    private final static int EXTRA_HEIGHT_OVERSCROLL = dp(48F);
    public final static int EXTRA_HEIGHT_FOOTER = dp(62F);

    public interface Callback {
        void onFullscreenAnimationStarted(boolean fullscreen);
        void onFullscreenAnimationEnded(boolean fullscreen);
    }

    private final int currentAccount;
    private final long dialogId;
    private final ActionBar actionBar;
    private final Theme.ResourcesProvider resourcesProvider;
    private float actionModeProgress = 0F;
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

    private final AnimatedFloat emojiFadeIn = new AnimatedFloat(this, 0, 440, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(20), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
    private int emojiColor;
    private boolean hasEmoji;
    private boolean isEmojiLoaded;
    private TLRPC.EmojiStatus emojiStatus;

    private final AnimatedFloat hasGiftsAnimated = new AnimatedFloat(this, 440, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final List<Gift> gifts = new ArrayList<>();
    private Gift pressedGift;

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final Avatar avatarView;
    private final ViewWithBlurredFooter avatarWrapper;
    private ImageLocation avatarLoadedLocation;

    private final float attractorMinY = -ATTRACTOR_HIDDEN_Y;
    private float attractorY;
    private float attractorMaxY;
    private float attractorProgress;

    private final ValueAnimator fullscreenAnimator = ValueAnimator.ofFloat(0F, 1F);
    private boolean isFullscreenAnimatorExpanding;
    private float fullscreenProgress;
    private boolean fullscreenProgressDrivenByTouch = true;
    public boolean discardGalleryImageOnFullscreenCollapse = false; // 'doNotSetForeground'

    private final AbsorbAnimation absorbAnimation = new AbsorbAnimation();

    private final ProfileGalleryView galleryView;
    private final ViewWithBlurredFooter galleryWrapper;
    private final ProfileOverlaysView overlaysView;
    private final ProfileActionsView actionsView;
    private final ProfileTextsView textsView;

    private Point displaySize;
    public Callback callback;
    private final boolean isTopic;

    public ProfileHeaderView(
            @NonNull Context context,
            int currentAccount,
            long dialogId,
            boolean isTopic,
            SizeNotifierFrameLayout root,
            @NonNull ActionBar actionBar,
            @NonNull ProfileGalleryView gallery,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.root = root;
        this.actionBar = actionBar;
        this.resourcesProvider = resourcesProvider;
        this.isTopic = isTopic;

        this.avatarView = new Avatar(context, currentAccount, dialogId, isTopic, resourcesProvider, this::updateRanges);
        this.avatarWrapper = new ViewWithBlurredFooter(avatarView, gallery, resourcesProvider);

        this.galleryView = gallery;
        this.galleryWrapper = new ViewWithBlurredFooter(galleryView, gallery, resourcesProvider);
        this.overlaysView = new ProfileOverlaysView(context, gallery, galleryWrapper.clipper);
        this.actionsView = new ProfileActionsView(context);
        this.textsView = new ProfileTextsView(context, resourcesProvider, actionBar, dialogId, currentAccount, isTopic);

        avatarDrawable.setProfile(true);
        galleryView.setParentAvatarImage(avatarView.image);
        galleryWrapper.setVisibility(View.INVISIBLE);

        fullscreenAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        fullscreenAnimator.addUpdateListener(anim -> {
            setFullscreenProgress((float) anim.getAnimatedValue(), true);
        });

        addView(avatarWrapper, createFrame(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        addView(galleryWrapper, createFrame(MATCH_PARENT, WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));
        addView(overlaysView, createFrame(MATCH_PARENT, WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));
        addView(actionsView, createFrame(MATCH_PARENT, WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));
        addView(textsView, createFrame(MATCH_PARENT, MATCH_PARENT));

        setWillNotDraw(false);
        setBackgroundColor(getThemedColor(Theme.key_avatar_backgroundActionBarBlue));
        updateGifts();
    }

    // MISC

    public ProfileTextsView getTexts() {
        return textsView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                updateGifts();
            }
        } else if (id == NotificationCenter.storiesUpdated || id == NotificationCenter.storiesReadUpdated) {
            avatarView.updateStoriesData();
        }
    }

    public void setDisplaySize(Point size) {
        this.displaySize = size;
        updateRanges();
    }

    public boolean setExpanded(boolean fullscreen, boolean animated) {
        int index = fullscreen ? 2 : 1;
        if (index >= snapGrowths.length) return false;
        changeGrowth(snapGrowths[index], animated);
        return true;
    }

    public boolean setCollapsed(boolean min, boolean animated) {
        int index = min ? 0 : 1;
        if (index >= snapGrowths.length) return false;
        changeGrowth(snapGrowths[index], animated);
        return true;
    }

    // GROWTH


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) updateRanges();
    }

    private void updateRanges() {
        if (displaySize == null) return;
        boolean landscape = displaySize.x > displaySize.y;

        // Configure base height
        int baseHeight = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? statusBarHeight : 0);
        if (baseHeight != this.baseHeight) {
            configureHeights(baseHeight);
        }

        int cutoutTop = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAttachedToWindow()) {
            DisplayCutout cutout = getRootWindowInsets().getDisplayCutout();
            cutoutTop = cutout == null ? 0 : cutout.getSafeInsetTop();
        }
        int availableHeight = displaySize.y - baseHeight;
        int avatarBottomPadding = AVATAR_BOTTOM_PADDING + (isTopic ? dp(30) : 0);

        // Configure growth
        int mid = HEIGHT_MID - ActionBar.getCurrentActionBarHeight() - statusBarHeight;
        int max = (getMeasuredWidth() == 0 ? displaySize.x : getMeasuredWidth()) - baseHeight + EXTRA_HEIGHT_FOOTER;
        if (actionBar.getOccupyStatusBar() && cutoutTop > 0) {
            // Not much room for the avatar. Ensure it's not clipped by cutouts.
            int leftover = (baseHeight + mid) - (avatarBottomPadding + AVATAR_SIZE);
            if (leftover < cutoutTop) mid += cutoutTop - leftover;
        }
        mid = Math.min((int) (.75F * (availableHeight - EXTRA_HEIGHT_OVERSCROLL)), mid);
        if (!landscape && !isTablet() && avatarView.getImageReceiver().hasNotThumb() && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
            configureGrowth(max + EXTRA_HEIGHT_OVERSCROLL, new int[]{0, mid, max});
        } else {
            configureGrowth(mid + EXTRA_HEIGHT_OVERSCROLL, new int[]{0, mid});
        }
        attractorMaxY = baseHeight + mid - avatarBottomPadding - AVATAR_SIZE /2F;
        if (growth > snapGrowths[snapGrowths.length - 1]) {
            changeGrowth(snapGrowths[snapGrowths.length - 1], false);
        }
    }

    @Override
    protected int onContentScroll(int dy, boolean touch) {
        if (dy > 0) {
            return dy; // no resistance when shrinking
        }
        if (touch && absorbAnimation.hasContact) {
            return Math.round(dy * 0.6F);
        }
        if (touch && snapGrowths.length >= 3 && growth > snapGrowths[1] && growth < snapGrowths[2]) {
            float progress = ((float) (growth - snapGrowths[1])) / (snapGrowths[2] - snapGrowths[1]);
            float factor = progress < FULLSCREEN_EXPAND_TRIGGER ? lerp(.3F, .15F, progress / FULLSCREEN_EXPAND_TRIGGER) : lerp(4F, .5F, progress);
            return Math.round(dy * factor);
        }
        int newGrowth = growth - dy;
        if (newGrowth >= snapGrowths[snapGrowths.length - 1]) { // overscroll
            if (touch) return Math.round(dy * 0.1F);
            return dy + newGrowth - snapGrowths[snapGrowths.length - 1];
        }
        return dy;
    }

    @Override
    protected void onGrowthChanged(int growth, int change, float velocity) {
        super.onGrowthChanged(growth, change, velocity);
        float hidden = -getTranslationY();

        attractorProgress = Math.min(1F, (float) growth / snapGrowths[1]);
        attractorY = lerp(attractorMinY, attractorMaxY, attractorProgress);
        float fullscreenTouchProgress = snapGrowths.length >= 3 ? Math.max(0, (float) (growth - snapGrowths[1]) / (snapGrowths[2] - snapGrowths[1])) : 0F;

        float cy;
        if (fullscreenTouchProgress > 1F) {
            cy = (baseHeight + growth - EXTRA_HEIGHT_FOOTER) / 2F;
        } else if (fullscreenTouchProgress > 0F) {
            cy = lerp(attractorMaxY, (baseHeight + snapGrowths[2] - EXTRA_HEIGHT_FOOTER) / 2F, fullscreenTouchProgress);
        } else {
            cy = attractorY;
        }
        avatarWrapper.translateContentCenter(hidden + cy);
        float gt = galleryWrapper.translateContentCenter(hidden + cy);
        overlaysView.setTranslationY(gt);
        overlaysView.setLayoutInsets(actionBar.getOccupyStatusBar() ? statusBarHeight : 0, EXTRA_HEIGHT_FOOTER);

        float shrinkInset = avatarView.updateAttractor(attractorProgress);
        avatarWrapper.clipper.setInset(shrinkInset);
        checkFullscreenAnimation(fullscreenTouchProgress, change, velocity);

        actionsView.setVisibleRoom(growth);
        textsView.updateLayout(baseHeight, growth, attractorProgress, fullscreenProgress);

        invalidate();
    }

    // FULLSCREEN ANIMATION

    public boolean isFullscreen() {
        return fullscreenProgress >= 1F;
    }

    private void setFullscreenProgress(float progress, boolean withinAnimation) {
        if (attractorProgress < 1F) {
            fullscreenProgressDrivenByTouch = true;
            fullscreenProgress = 0F;
        } else if (withinAnimation) {
            fullscreenProgressDrivenByTouch = false;
            fullscreenProgress = progress;
        } else {
            if (progress >= 1F || progress <= FULLSCREEN_EXPAND_TRIGGER) fullscreenProgressDrivenByTouch = true;
            fullscreenProgress = fullscreenProgressDrivenByTouch ? progress : fullscreenProgress;
        }

        // Use first part for the stories ring to disappear
        float initial = Math.min(1F, fullscreenProgress / FULLSCREEN_EXPAND_TRIGGER);
        avatarView.updateStoriesInsets(initial);

        // Use the rest for blurred footer appearance
        float remaining = Math.max(0F, (fullscreenProgress - FULLSCREEN_EXPAND_TRIGGER) / (1F - FULLSCREEN_EXPAND_TRIGGER));
        galleryWrapper.setFooterVisibility(Math.min(1F, remaining));
        avatarWrapper.setFooterVisibility(Math.min(1F, remaining));
        overlaysView.setGalleryVisibility(remaining);

        // Update size
        int fullscreenSize = baseHeight + snapGrowths[snapGrowths.length - 1] - EXTRA_HEIGHT_FOOTER;
        float targetWidth = remaining == 0F
                ? lerp(AVATAR_SIZE, 1.25F*AVATAR_SIZE, initial)
                : lerp(1.25F*AVATAR_SIZE, fullscreenSize, (float) Math.pow(remaining, .75));
        avatarWrapper.setScaleX(targetWidth / AVATAR_SIZE);
        avatarWrapper.setScaleY(targetWidth / AVATAR_SIZE);
        avatarWrapper.clipper.invalidate();
        galleryWrapper.setScaleX(targetWidth / fullscreenSize);
        galleryWrapper.setScaleY(targetWidth / fullscreenSize);
        galleryWrapper.clipper.invalidate();
        overlaysView.setScaleX(targetWidth / fullscreenSize);
        overlaysView.setScaleY(targetWidth / fullscreenSize);

        // Update radius
        float targetRadius = remaining == 0F ? targetWidth/2F : lerp(1.25F*AVATAR_SIZE/2F, 0, CubicBezierInterpolator.EASE_BOTH.getInterpolation(remaining));
        avatarWrapper.clipper.setRoundRadius(targetRadius);
        galleryWrapper.clipper.setRoundRadius(targetRadius);

        // Misc
        avatarView.image.setForegroundAlpha(Math.min(1F, fullscreenProgress));
        textsView.updateLayout(baseHeight, growth, attractorProgress, fullscreenProgress);
    }

    private void checkFullscreenAnimation(float touch, int change, float velocity) {
        if (attractorProgress < 1F) {
            if (fullscreenAnimator.isRunning()) fullscreenAnimator.cancel();
            setFullscreenProgress(0F, false);
            return;
        }
        float max = Math.max(fullscreenProgress, touch);
        float min = Math.min(fullscreenProgress, touch);
        if (max > FULLSCREEN_EXPAND_TRIGGER && fullscreenProgress < 1F && change > 0) {
            if (fullscreenAnimator.isRunning() && isFullscreenAnimatorExpanding) return;
            launchFullscreenAnimation(true, 1F, velocity);
        } else if (min < FULLSCREEN_COLLAPSE_TRIGGER && fullscreenProgress > FULLSCREEN_EXPAND_TRIGGER && change < 0) {
            if (fullscreenAnimator.isRunning() && !isFullscreenAnimatorExpanding) return;
            launchFullscreenAnimation(false, FULLSCREEN_EXPAND_TRIGGER, velocity);
        } else if (!fullscreenAnimator.isRunning()) {
            setFullscreenProgress(touch, false);
        }
    }

    private void launchFullscreenAnimation(boolean expand, float destination, float velocity) {
        float current = fullscreenProgress;
        fullscreenAnimator.cancel();
        fullscreenAnimator.setFloatValues(current, destination);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);

        float accelerator = clamp(Math.abs(velocity), dpf2(2000F), dpf2(1100F)) / dpf2(1100f);
        fullscreenAnimator.setDuration((long) (200F / accelerator));

        if (expand) {
            galleryView.setCreateThumbFromParent(true);
            galleryView.getAdapter().notifyDataSetChanged();
            galleryView.setAnimatedFileMaybe(avatarView.getImageReceiver().getAnimation());
            updateAvatarImageFromGallery(false, false);
        } else {
            avatarView.getImageReceiver().setAllowStartAnimation(true);
            avatarView.getImageReceiver().startAnimation();
            updateAvatarImageFromGallery(true, false);
            avatarView.image.setForegroundAlpha(1F);
            galleryWrapper.setVisibility(View.INVISIBLE);
        }
        if (callback != null) {
            callback.onFullscreenAnimationStarted(expand);
        }

        fullscreenAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                cleanup();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                cleanup();
                if (callback != null) {
                    callback.onFullscreenAnimationEnded(expand);
                }
                if (expand) {
                    galleryWrapper.setVisibility(View.VISIBLE);
                    avatarView.image.clearForeground();
                } else {
                    galleryView.resetCurrentItem();
                }
                discardGalleryImageOnFullscreenCollapse = false;
            }

            private void cleanup() {
                fullscreenAnimator.removeListener(this);
                float touchProgress = snapGrowths.length >= 3 ? Math.max(0, (float) (growth - snapGrowths[1]) / (snapGrowths[2] - snapGrowths[1])) : 0F;
                setFullscreenProgress(touchProgress, false);
            }
        });
        fullscreenAnimator.start();
        isFullscreenAnimatorExpanding = expand;
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
        arrayList.add(new ThemeDescription(avatarView, 0, null, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(avatarView, 0, null, null, new Drawable[]{avatarDrawable}, null, Theme.key_avatar_backgroundInProfileBlue));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public int getAverageColor() {
        return hasTheme ? ColorUtils.blendARGB(themeColor1, themeColor2, 0.25f) : plainColor;
    }

    public void updateColors(MessagesController.PeerColor peerColor, float actionModeProgress, boolean animated) {
        if (peerColor != null) {
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
        emoji.setColor(emojiColor);
        hasTheme = peerColor != null;
        if (!animated) {
            themeColor1Animated.set(themeColor1, true);
            themeColor2Animated.set(themeColor2, true);
        }
        this.actionModeProgress = actionModeProgress;
        avatarView.stories.setActionBarActionMode(actionModeProgress);
        textsView.updateColors(peerColor, actionModeProgress);
        invalidate();
        updateGifts();
    }

    // EMOJI

    public void setEmojiInfo(long emojiId, TLRPC.EmojiStatus emojiStatus, boolean animated) {
        emoji.set(emojiId, animated);
        emoji.setColor(emojiColor);
        if (!animated) emojiFadeIn.force(true);
        hasEmoji = hasEmoji || emojiId != 0 && emojiId != -1;
        invalidate();

        this.emojiStatus = emojiStatus;
        updateGifts();
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
        for (Gift gift : gifts) {
            gift.attach(this, true);
        }
        emoji.attach();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesReadUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesReadUpdated);
        emoji.detach();
        for (Gift gift : gifts) {
            gift.attach(this, false);
        }
    }

    // GIFTS

    private static class Gift extends Drawable implements StarGiftPatterns.RadialPatternElement {
        private boolean attached;
        private final TL_stars.TL_starGiftUnique content;
        private final RadialGradient gradientShader;
        private final Matrix gradientMatrix = new Matrix();
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedEmojiDrawable emoji;
        private float[] radialInfo = GIFTS_LAYOUT[0];
        public ButtonBounce bounce;

        private Gift(TL_stars.TL_starGiftUnique gift, int currentAccount) {
            super();
            this.content = gift;
            TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            int color = backdrop.center_color | 0xFF000000;
            gradientShader = new RadialGradient(0.5F, 0.5F, 0.5F, color, ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP);
            gradientPaint.setShader(gradientShader);
            emoji = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, gift.getDocument());
        }

        @Override
        public float[] getRadialInfo() {
            return radialInfo;
        }

        @Override
        public Drawable getRadialDrawable() {
            return this;
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            Rect copy = new Rect(bounds);
            float inset = bounds.width() - (float) bounds.width() / GIFT_LAYOUT_INSET;
            copy.inset((int) (inset / 2), (int) (inset / 2));
            emoji.setBounds(copy);
        }

        @Override
        public void setAlpha(int alpha) {
            emoji.setAlpha(alpha);
            gradientPaint.setAlpha(alpha);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            gradientMatrix.setScale(bounds.width(), bounds.width());
            gradientMatrix.postTranslate(bounds.left, bounds.top);
            gradientShader.setLocalMatrix(gradientMatrix);
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, gradientPaint);
            if (bounce != null) {
                float scale = 1F + 0.15F * bounce.isPressedProgress();
                canvas.save();
                canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
                emoji.draw(canvas);
                canvas.restore();
            } else {
                emoji.draw(canvas);
            }
        }

        private void attach(View view, boolean attached) {
            if (attached == this.attached) return;
            this.attached = attached;
            if (attached) emoji.addView(view);
            else emoji.removeView(view);
            bounce = attached ? new ButtonBounce(view) : null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY() + getTranslationY(); // used to draw the gifts
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (Gift gift : gifts) {
                    if (gift.getBounds().contains((int) x, (int) y)) {
                        pressedGift = gift;
                        pressedGift.bounce.setPressed(true);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pressedGift != null && pressedGift.getBounds().contains((int) x, (int) y)) {
                    Browser.openUrl(getContext(), "https://t.me/nft/" + pressedGift.content.slug);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_MOVE:
                if (pressedGift != null) pressedGift.bounce.setPressed(false);
                pressedGift = null;
                break;
        }
        return pressedGift != null;
    }

    public void updateGifts() {
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) return;

        List<Gift> oldGifts = gifts;
        List<Gift> newGifts = new ArrayList<>();
        Set<Long> ignored = new HashSet<>();
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            ignored.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }

        StarsController.GiftsList list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved
                        && savedGift.pinned_to_top
                        && savedGift.gift instanceof TL_stars.TL_starGiftUnique
                        && savedGift.gift.getDocument() != null
                        && ignored.add(savedGift.gift.id)) {
                    Gift reused = null;
                    for (Gift gift : oldGifts) {
                        if (gift.content.id == savedGift.gift.id) {
                            reused = gift;
                            break;
                        }
                    }
                    if (reused != null) oldGifts.remove(reused);
                    reused = reused != null ? reused : new Gift((TL_stars.TL_starGiftUnique) savedGift.gift, currentAccount);
                    reused.radialInfo = GIFTS_LAYOUT[newGifts.size()];
                    newGifts.add(reused);
                    if (newGifts.size() == GIFTS_LAYOUT.length) break;
                }
            }
        }

        boolean unchanged = newGifts.size() == gifts.size() && IntStream
                .range(0, gifts.size())
                .allMatch(i -> gifts.get(i).content.id == newGifts.get(i).content.id);
        for (Gift gift : oldGifts) {
            gift.attach(this, false);
        }
        this.gifts.clear();
        this.gifts.addAll(newGifts);
        if (isAttachedToWindow()) {
            for (Gift gift : newGifts) {
                gift.attach(this, true);
            }
        }
        if (!unchanged) invalidate();
    }

    // GALLERY

    public ProfileGalleryView getGallery() {
        return galleryView;
    }

    // AVATAR

    public void setUploadProgress(float progress, ImageLocation uploadingLocation) {
        avatarView.progressBar.setProgress(progress);
        galleryView.setUploadProgress(uploadingLocation, progress);
    }

    public void setUploadStarted(ImageLocation big, ImageLocation small) {
        avatarView.image.setImage(small, "50_50", avatarDrawable, null);
        galleryView.addUploadingImage(big, small);
        avatarView.updateProgressBar(true, true);
    }

    public void setUploadCompletedOrCancelled(ImageLocation big) {
        galleryView.scrolledByUser = true;
        galleryView.removeUploadingImage(big);
        galleryView.setCreateThumbFromParent(false);
        avatarView.updateProgressBar(false, true);
    }

    public Avatar getAvatar() {
        return avatarView;
    }

    public void setAvatarUser(@NonNull TLRPC.User user, TLRPC.UserFull userInfo, TLRPC.FileLocation uploadingSmall, TLRPC.FileLocation uploadingBig) {
        avatarDrawable.setInfo(currentAccount, user);

        final ImageLocation imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG);
        final ImageLocation thumbLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
        final ImageLocation videoThumbLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_VIDEO_BIG);
        VectorAvatarThumbDrawable vectorAvatarThumbDrawable = null;
        TLRPC.VideoSize vectorAvatar = null;
        if (userInfo != null) {
            vectorAvatar = FileLoader.getVectorMarkupVideoSize(user.photo != null && user.photo.personal ? userInfo.personal_photo : userInfo.profile_photo);
            if (vectorAvatar != null) {
                vectorAvatarThumbDrawable = new VectorAvatarThumbDrawable(vectorAvatar, user.premium, VectorAvatarThumbDrawable.TYPE_PROFILE);
            }
        }
        final ImageLocation videoLocation = galleryView.getCurrentVideoLocation(thumbLocation, imageLocation);
        if (uploadingSmall == null) {
            galleryView.initIfEmpty(vectorAvatarThumbDrawable, imageLocation, thumbLocation, true);
        }
        if (uploadingBig == null) {
            if (vectorAvatar != null) {
                avatarView.image.setImageDrawable(vectorAvatarThumbDrawable);
            } else if (videoThumbLocation != null && !user.photo.personal) {
                avatarView.image.getImageReceiver().setVideoThumbIsSame(true);
                avatarView.image.setImage(videoThumbLocation, "avatar", thumbLocation, "50_50", avatarDrawable, user);
            } else {
                avatarView.image.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, thumbLocation, "50_50", avatarDrawable, user);
            }
        }
        onAvatarChanged(user, imageLocation, user.photo != null ? user.photo.photo_big : null);
    }

    public void setAvatarChat(@NonNull TLRPC.Chat chat, long topicId, TLRPC.FileLocation uploadingBig) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        chat = ChatObject.isMonoForum(chat) ? controller.getMonoForumLinkedChat(chat.id) : chat;

        ImageLocation imageLocation = null;
        ImageLocation thumbLocation = null;
        ImageLocation videoLocation = null;
        if (topicId == 0) {
            avatarDrawable.setInfo(currentAccount, chat);
            imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
            thumbLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            videoLocation = galleryView.getCurrentVideoLocation(thumbLocation, imageLocation);
        } else {
            TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topicId);
            ForumUtilities.setTopicIcon(avatarView.image, topic, true, true, resourcesProvider);
        }

        galleryView.initIfEmpty(null, imageLocation, thumbLocation, true);
        if (uploadingBig == null) {
            String filter = videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
            avatarView.image.setImage(videoLocation, filter, thumbLocation, "50_50", avatarDrawable, chat);
        }
        onAvatarChanged(chat, imageLocation, chat.photo != null && topicId == 0 ? chat.photo.photo_big : null);
    }

    public void unsetAvatar() {
        avatarView.image.setImageDrawable(null);
        galleryView.onDestroy();
    }

    private void onAvatarChanged(Object parentObject, ImageLocation imageLocation, TLRPC.FileLocation photoBig) {
        if (imageLocation != null && (avatarLoadedLocation == null || imageLocation.photoId != avatarLoadedLocation.photoId)) {
            avatarLoadedLocation = imageLocation;
            FileLoader.getInstance(currentAccount).loadFile(imageLocation, parentObject, null, FileLoader.PRIORITY_LOW, 1);
        }
        avatarView.image.getImageReceiver().setVisible(
                !PhotoViewer.isShowingImage(photoBig), // WIP: && (getLastStoryViewer() == null || getLastStoryViewer().transitionViewHolder.view != avatarImage),
                true
        );
    }

    public void updateAvatarImageFromGallery(boolean isCollapse, boolean addSecondParent) {
        if (isCollapse) {
            if (discardGalleryImageOnFullscreenCollapse) return;
            BackupImageView imageView = galleryView.getCurrentItemView();
            if (imageView != null) {
                if (imageView.getImageReceiver().getDrawable() instanceof VectorAvatarThumbDrawable) {
                    avatarView.image.drawForeground(false);
                } else {
                    avatarView.image.drawForeground(true);
                    avatarView.image.setForegroundImage(imageView.getImageReceiver().getDrawableSafe());
                }
            }
        } else {
            // Formerly setForegroundImage(boolean secondParent)
            Drawable drawable = avatarView.getImageReceiver().getDrawable();
            if (drawable instanceof VectorAvatarThumbDrawable) {
                avatarView.image.setForegroundImage(null, null, drawable);
            } else if (drawable instanceof AnimatedFileDrawable) {
                avatarView.image.setForegroundImage(null, null, drawable);
                if (addSecondParent) {
                    ((AnimatedFileDrawable) drawable).addSecondParentView(avatarView.image);
                }
            } else {
                ImageLocation location = galleryView.getImageLocation(0);
                String filter = location != null && location.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? "avatar" : null;
                avatarView.image.setForegroundImage(location, filter, drawable);
            }
        }
    }

    public static class Avatar extends FrameLayout {
        private final static float MIN_RADIUS = ATTRACTOR_HIDDEN_Y;
        private final static float MAX_INSET = AVATAR_SIZE/2F - MIN_RADIUS;

        private final AvatarImageView image;
        private final ProfileStoriesView stories;
        private final RadialProgressView progressBar;

        public Callback callback;

        public interface Callback {
            void onAvatarClick(@Nullable StoryViewer.PlaceProvider provider);
            boolean onAvatarLongClick();
        }

        private Avatar(Context context, int currentAccount, long dialogId, boolean isTopic, Theme.ResourcesProvider resourcesProvider, Runnable updateRanges) {
            super(context);
            image = new AvatarImageView(context) {
                @Override
                public void onNewImageSet() {
                    super.onNewImageSet();
                    updateRanges.run();
                }

                @Override
                public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    if (getImageReceiver().hasNotThumb()) {
                        info.setText(LocaleController.getString(R.string.AccDescrProfilePicture));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString(R.string.Open)));
                            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, LocaleController.getString(R.string.AccDescrOpenInPhotoViewer)));
                        }
                    } else {
                        info.setVisibleToUser(false);
                    }
                }
            };
            image.getImageReceiver().setAllowDecodeSingleFrame(true);
            image.getImageReceiver().setAllowStartAnimation(true);
            image.getImageReceiver().startAnimation();
            image.setOnClickListener(v -> { if (callback != null) callback.onAvatarClick(null); });
            image.setOnLongClickListener(v -> callback != null && callback.onAvatarLongClick());

            stories = new ProfileStoriesView(context, currentAccount, dialogId, isTopic, image, resourcesProvider) {
                @Override
                protected void onTap(StoryViewer.PlaceProvider provider) {
                    if (callback != null) callback.onAvatarClick(provider);
                }

                @Override
                protected void onLongPress() {
                    if (callback != null) callback.onAvatarLongClick();
                }
            };

            progressBar = new RadialProgressView(context) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); {
                    paint.setColor(0x55000000);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (getImageReceiver().hasNotThumb()) {
                        paint.setAlpha((int) (0x55 * getImageReceiver().getCurrentAlpha()));
                        canvas.drawCircle(getWidth() / 2F, getHeight() / 2F, getWidth() / 2F, paint);
                    }
                    super.onDraw(canvas);
                }
            };
            progressBar.setSize(AndroidUtilities.dp(26));
            progressBar.setProgressColor(0xffffffff);
            progressBar.setNoProgress(false);

            addView(image, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));
            addView(stories, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));
            addView(progressBar, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));
            updateStoriesData();
            updateStoriesInsets(0F);
            updateProgressBar(false, false);
            updateAttractor(0F);
        }

        public ImageReceiver getImageReceiver() {
            return image.getImageReceiver();
        }

        public AvatarImageView getImageView() {
            return image;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int spec = MeasureSpec.makeMeasureSpec(AVATAR_SIZE, MeasureSpec.EXACTLY);
            super.onMeasure(spec, spec);
        }

        private float updateAttractor(float attractorProgress) {
            float progress = clamp((attractorProgress - .1F) / .8F, 1F, 0F); // trim 10% on both sides
            if (progress >= .5F) {
                image.setDimAlpha(0);
                image.setBlurAlpha(2F * (1F - progress));
            } else {
                image.setDimAlpha((1F - progress * 2F));
                image.setBlurAlpha(1);
            }
            return lerp(MAX_INSET, 0F, CubicBezierInterpolator.EASE_IN.getInterpolation(attractorProgress));
        }

        private void updateStoriesInsets(float storiesDisappearProgress) {
            image.setProgressToStoriesInsets(1F - storiesDisappearProgress);
            image.setRoundRadius(storiesDisappearProgress == 1F ? 0 : AVATAR_SIZE/2);
            stories.setAlpha(1F - storiesDisappearProgress);
        }

        public void updateStoriesData() {
            boolean has = stories.updateStories();
            image.setHasStories(has);
        }

        private void updateProgressBar(boolean show, boolean animated) {
            AndroidUtilities.updateViewVisibilityAnimated(progressBar, show, 1F, animated);
        }
    }

    // ABSORB ANIMATION

    private static class AbsorbAnimation {
        private final static float AVATAR_TOP_CONTACT = dpf2(10);
        private final static float AVATAR_TOP_INITIAL = dpf2(20);
        private final static float TOP_W_MIN = dpf2(24);
        private final static float TOP_W_CONTACT = dpf2(48);
        private final static float TOP_W_MAX = dpf2(64);

        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF avatarBox = new RectF();
        private final RectF avatarFullBox = new RectF();
        private boolean hasContact;
        private float progress;

        private AbsorbAnimation() {
            paint.setColor(Color.BLACK);
        }

        private boolean shouldDraw(ViewClipper avatar, float avatarCenterY, float avatarMinCenterY, float avatarProgress) {
            avatar.setCustom(null, 0, 0, false);
            if (avatarProgress == 0F || avatarProgress == 1F) {
                hasContact = false;
                return false;
            }
            float avatarHalfSize = avatar.getBoundingBox().width() / 2F;
            float avatarHalfSizeWithoutClip = avatar.getBoundingBox().centerX();
            avatarBox.set(-avatarHalfSize, avatarCenterY - avatarHalfSize, avatarHalfSize, avatarCenterY + avatarHalfSize);
            avatarFullBox.set(-avatarHalfSizeWithoutClip, avatarCenterY - avatarHalfSizeWithoutClip, avatarHalfSizeWithoutClip, avatarCenterY + avatarHalfSizeWithoutClip);
            hasContact = avatarBox.top <= AVATAR_TOP_CONTACT;
            if (avatarBox.top > AVATAR_TOP_INITIAL) return false;
            if (hasContact) {
                float avatarTopFinal = avatarMinCenterY - Avatar.MIN_RADIUS;
                progress = (AVATAR_TOP_CONTACT - avatarBox.top) / (AVATAR_TOP_CONTACT - avatarTopFinal);
            } else {
                progress = (AVATAR_TOP_INITIAL - avatarBox.top) / (AVATAR_TOP_INITIAL - AVATAR_TOP_CONTACT);
            }
            return true;
        }

        private void draw(Canvas canvas, ViewClipper avatar) {
            if (hasContact) {
                drawHourglass(canvas);
                avatar.setCustom(path, avatarFullBox.left, avatarFullBox.top, false);
            } else {
                drawDetachedTopPath(canvas);
                drawDetachedBottomPath(canvas);
            }
        }

        private void drawGaussian(Canvas canvas, float w, float h) {
            float o = 0.3F * w;
            path.rewind();
            path.moveTo(-w/2, 0);
            path.cubicTo(-w/2+o, 0, -o, h, 0, h);
            path.cubicTo(o, h, w/2-o, 0, w/2, 0);
            canvas.drawPath(path, paint);
        }

        private void drawDetachedTopPath(Canvas canvas) {
            float w = lerp(TOP_W_MIN, TOP_W_CONTACT, progress);
            float h = 0.75F * AVATAR_TOP_CONTACT * progress;
            drawGaussian(canvas, w, h);
        }

        private void drawDetachedBottomPath(Canvas canvas) {
            float avatarHalfSize = avatarBox.right;
            float h = 0.25F * AVATAR_TOP_CONTACT * progress;
            float e = avatarHalfSize * 0.076120F;
            canvas.translate(0, avatarBox.top + e);
            drawGaussian(canvas, avatarHalfSize * 0.765367F, -(h+e));
            canvas.translate(0, -(avatarBox.top + e));
        }

        private void drawHourglass(Canvas canvas) {
            float avatarHalfSize = avatarBox.right;
            double angle = Math.PI * lerp(1F/8, 7F/8, CubicBezierInterpolator.EASE_OUT.getInterpolation(progress));
            float degrees = (float) (angle * 180/Math.PI);
            float tx = lerp(TOP_W_CONTACT, TOP_W_MAX, progress) / 2;
            float bx = avatarHalfSize * (float) Math.sin(angle);
            float by = avatarBox.centerY() - avatarHalfSize * (float) Math.cos(angle);

            float tan = (float) Math.tan(Math.PI * lerp(.3, .1, progress));
            float cx = tx + 0.5F * ((bx-tx) - tan*by);
            float cy = Math.max(0F, 0.5F * (by + tan*(bx-tx)));
            path.rewind();
            path.moveTo(tx, 0);
            path.quadTo(cx, cy, bx, by);
            path.arcTo(avatarBox, degrees - 90, 2 * (180 - degrees), false);
            path.lineTo(-bx, by);
            path.quadTo(-cx, cy, -tx, 0);
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    // ACTIONS


    public ProfileActionsView getActions() {
        return actionsView;
    }

    // DRAW

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float hidden = -getTranslationY();
        canvas.translate(0, hidden);
        int visible = getMeasuredHeight() - (int) hidden;
        int paintable = (int) (visible * (1.0f - actionModeProgress));
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

            // Blend them based on theme progress
            // WIP: themeProgress factor is (playProfileAnimation == 0 ? 1f : avatarAnimationProgress)
            final float themeProgress = hasThemeAnimated.set(hasTheme);
            if (themeProgress < 1) {
                canvas.drawRect(0, 0, width, paintable, plainPaint);
            }
            if (themeProgress > 0) {
                plainPaint.setAlpha((int) (0xFF * themeProgress));
                plainPaint.setColor(themeBackground);
                canvas.drawRect(0, 0, width, paintable, plainPaint);

                // Gradient: as the header collapses, it translates, shrinks and fades
                float shrink = lerp(.75F, 1F, attractorProgress);
                float alpha = lerp(.25F, .5F, attractorProgress);
                float size = Math.min(width - dp(72), dp(398));
                gradientMatrix.setScale(size, size);
                gradientMatrix.postTranslate((width - size) / 2F, attractorY - size / 2F);
                gradientMatrix.postScale(shrink, shrink, width / 2F, attractorY);
                gradient.setLocalMatrix(gradientMatrix);
                gradientPaint.setAlpha((int) (0xFF * themeProgress * alpha));
                canvas.drawRect(0, 0, width, paintable, gradientPaint);
            }

            // Emoji pattern
            if (hasEmoji && isEmojiLoaded() && attractorProgress > 0F) {
                float alpha = lerp(.2F, .5F, attractorProgress);
                float progress = emojiFadeIn.set(isEmojiLoaded) * attractorProgress;
                float overscroll = clamp(fullscreenProgress / FULLSCREEN_EXPAND_TRIGGER, 1, 0);
                StarGiftPatterns.drawRadialPattern(canvas, emoji, width / 2F, attractorY, attractorMaxY, alpha, progress, overscroll);
            } else {
                emojiFadeIn.set(isEmojiLoaded);
            }

            // Gift pattern
            if (!gifts.isEmpty() && attractorProgress > 0F) {
                float progress = hasGiftsAnimated.set(true) * attractorProgress;
                float overscroll = clamp(fullscreenProgress / FULLSCREEN_EXPAND_TRIGGER, 1, 0);
                StarGiftPatterns.drawRadialPattern(canvas, gifts, width / 2F, attractorY, attractorMaxY, -1F, progress, overscroll);
            } else {
                hasGiftsAnimated.set(false);
            }

            // Absorb animation
            if (absorbAnimation.shouldDraw(avatarWrapper.clipper, attractorY, attractorMinY, attractorProgress)) {
                canvas.save();
                canvas.translate(avatarWrapper.getX() + avatarWrapper.getWidth() / 2F, 0);
                absorbAnimation.draw(canvas, avatarWrapper.clipper);
                canvas.restore();
            }
        }

        if (paintable != visible) {
            int color = getThemedColor(Theme.key_windowBackgroundWhite);
            plainPaint.setColor(color);
            blurBounds.set(0, paintable, width, visible);
            root.drawBlurRect(canvas, 0, blurBounds, plainPaint, true);
        }

        /*WIP: if (parentLayout != null) {
            parentLayout.drawHeaderShadow(canvas, (int) (headerShadowAlpha * 255), (int) visible);
        } */

        canvas.restore();
    }
}
