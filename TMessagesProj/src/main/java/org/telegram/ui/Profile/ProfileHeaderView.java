package org.telegram.ui.Profile;

import android.content.Context;
import android.graphics.*;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
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

import java.util.*;
import java.util.stream.IntStream;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
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

    private final AnimatedFloat hasGiftsAnimated = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final List<Gift> gifts = new ArrayList<>();
    private Gift pressedGift;

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final AvatarImageView avatarImage;
    private ImageLocation avatarLoadedLocation;

    private TLRPC.FileLocation uploadedAvatarSmall;
    private TLRPC.FileLocation uploadedAvatarBig;

    private final float attractorHiddenCenterY = dp(16);

    public ProfileHeaderView(
            @NonNull Context context,
            int currentAccount,
            long dialogId,
            SizeNotifierFrameLayout root,
            @NonNull ActionBar actionBar,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.root = root;
        this.actionBar = actionBar;
        this.resourcesProvider = resourcesProvider;
        this.avatarImage = new AvatarImageView(context) {
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
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (animatedEmojiDrawable != null && animatedEmojiDrawable.getImageReceiver() != null) {
                    animatedEmojiDrawable.getImageReceiver().startAnimation();
                }
            }
        };

        avatarImage.getImageReceiver().setAllowDecodeSingleFrame(true);
        avatarDrawable.setProfile(true);
        avatarDrawable.setRoundRadius(dp(45));
        avatarImage.setRoundRadius(dp(45));

        addView(avatarImage, new FrameLayout.LayoutParams(dp(90), dp(90), Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        setWillNotDraw(false);
        setBackgroundColor(getThemedColor(Theme.key_avatar_backgroundActionBarBlue));
    }

    // MISC

    public void setActionModeProgress(float progress) {
        actionModeProgress = progress;
        invalidate();
    }

    public void setDisplaySize(Point size) {
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

    // GROWTH

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
        float hidden = -getTranslationY();
        float visible = getMeasuredHeight() - hidden;
        float defaultHalfSize = avatarImage.getMeasuredWidth() / 2F;
        float centerY;
        float scale;
        if (growth >= snapGrowths[1] && snapGrowths.length > 2) {
            float progress = (float) (growth - snapGrowths[1]) / (snapGrowths[2] - snapGrowths[1]);
            centerY = visible / 2F;
            scale = lerp(1F, 2F, progress);
        } else {
            float progress = Math.min(1F, (float) growth / snapGrowths[1]);
            centerY = lerp(-attractorHiddenCenterY, visible / 2F, CubicBezierInterpolator.EASE_OUT.getInterpolation(progress));
            scale = lerp(attractorHiddenCenterY/defaultHalfSize, 1F, CubicBezierInterpolator.EASE_IN.getInterpolation(progress));
        }
        avatarImage.setTranslationY(hidden + centerY - defaultHalfSize);
        avatarImage.setScaleX(scale);
        avatarImage.setScaleY(scale);
        avatarImage.setVisibility(centerY == 0F ? View.INVISIBLE : View.VISIBLE);
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
        arrayList.add(new ThemeDescription(avatarImage, 0, null, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(avatarImage, 0, null, null, new Drawable[]{avatarDrawable}, null, Theme.key_avatar_backgroundInProfileBlue));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public int getAverageColor() {
        return hasTheme ? ColorUtils.blendARGB(themeColor1, themeColor2, 0.25f) : plainColor;
    }

    public void updateColors(MessagesController.PeerColor peerColor, boolean animated) {
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
        getAverageColor();
        if (!animated) {
            themeColor1Animated.set(themeColor1, true);
            themeColor2Animated.set(themeColor2, true);
        }
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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        emoji.detach();
        for (Gift gift : gifts) {
            gift.attach(this, false);
        }
    }

    // GIFTS


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                updateGifts();
            }
        }
    }

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
        float x = event.getX() + getTranslationX();
        float y = event.getY() + getTranslationY();
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

    // AVATAR

    public void setAvatarUser(@NonNull TLRPC.User user, TLRPC.UserFull userInfo) {
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
        final ImageLocation videoLocation = null; // WIP: avatarsViewPager.getCurrentVideoLocation(thumbLocation, imageLocation);
        if (uploadedAvatarSmall == null) {
            // WIP: avatarsViewPager.initIfEmpty(vectorAvatarThumbDrawable, imageLocation, thumbLocation, reload);
        }
        if (uploadedAvatarBig == null) {
            if (vectorAvatar != null) {
                avatarImage.setImageDrawable(vectorAvatarThumbDrawable);
            } else if (videoThumbLocation != null && !user.photo.personal) {
                avatarImage.getImageReceiver().setVideoThumbIsSame(true);
                avatarImage.setImage(videoThumbLocation, "avatar", thumbLocation, "50_50", avatarDrawable, user);
            } else {
                avatarImage.setImage(videoLocation, ImageLoader.AUTOPLAY_FILTER, thumbLocation, "50_50", avatarDrawable, user);
            }
        }
        onAvatarChanged(user, imageLocation, user.photo != null ? user.photo.photo_big : null);
    }

    public void setAvatarChat(@NonNull TLRPC.Chat chat, long topicId) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        chat = ChatObject.isMonoForum(chat) ? controller.getMonoForumLinkedChat(chat.id) : chat;

        ImageLocation imageLocation = null;
        ImageLocation thumbLocation = null;
        ImageLocation videoLocation = null;
        if (topicId == 0) {
            avatarDrawable.setInfo(currentAccount, chat);
            imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_BIG);
            thumbLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
            // WIP: videoLocation = avatarsViewPager.getCurrentVideoLocation(thumbLocation, imageLocation);
        } else {
            TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chat.id, topicId);
            ForumUtilities.setTopicIcon(avatarImage, topic, true, true, resourcesProvider);
        }

        // WIP: avatarsViewPager.initIfEmpty(null, imageLocation, thumbLocation, reload);
        if (uploadedAvatarBig == null) {
            String filter = videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION ? ImageLoader.AUTOPLAY_FILTER : null;
            avatarImage.setImage(videoLocation, filter, thumbLocation, "50_50", avatarDrawable, chat);
        }
        onAvatarChanged(chat, imageLocation, chat.photo != null && topicId == 0 ? chat.photo.photo_big : null);
    }

    public void unsetAvatar() {
        avatarImage.setImageDrawable(null);
        // WIP: avatarsViewPager.onDestroy();
    }

    private void onAvatarChanged(Object parentObject, ImageLocation imageLocation, TLRPC.FileLocation photoBig) {
        if (imageLocation != null && (avatarLoadedLocation == null || imageLocation.photoId != avatarLoadedLocation.photoId)) {
            avatarLoadedLocation = imageLocation;
            FileLoader.getInstance(currentAccount).loadFile(imageLocation, parentObject, null, FileLoader.PRIORITY_LOW, 1);
        }
        avatarImage.getImageReceiver().setVisible(
                !PhotoViewer.isShowingImage(photoBig), // WIP: && (getLastStoryViewer() == null || getLastStoryViewer().transitionViewHolder.view != avatarImage),
                true // WIP: storyView != null
        );
    }

    // GENERIC

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

            // Compute animation progress
            // WIP: themeProgress factor is (playProfileAnimation == 0 ? 1f : avatarAnimationProgress)
            final float themeProgress = hasThemeAnimated.set(hasTheme);
            float growthProgress = Math.min(1F, (float) growth / snapGrowths[1]);
            float attractorY = lerp(-attractorHiddenCenterY, paintable / 2F, growthProgress);

            // Blend colors based on progress
            if (themeProgress < 1) {
                canvas.drawRect(0, 0, width, paintable, plainPaint);
            }
            if (themeProgress > 0) {
                plainPaint.setAlpha((int) (0xFF * themeProgress));
                plainPaint.setColor(themeBackground);
                canvas.drawRect(0, 0, width, paintable, plainPaint);

                // Gradient: as the header collapses, it translates, shrinks and fades
                float shrink = lerp(0.6F, 1F, growthProgress);
                float alpha = lerp(0.2F, 0.5F, growthProgress);
                float size = Math.min(width - dp(72), dp(398));
                gradientMatrix.setScale(size, size);
                gradientMatrix.postTranslate((width - size) / 2F, attractorY - size / 2F);
                gradientMatrix.postScale(shrink, shrink, width / 2F, attractorY);
                gradient.setLocalMatrix(gradientMatrix);
                gradientPaint.setAlpha((int) (0xFF * themeProgress * alpha));
                canvas.drawRect(0, 0, width, paintable, gradientPaint);
            }

            // Emoji pattern
            if (hasEmoji && isEmojiLoaded() && growthProgress > 0F) {
                float alpha = lerp(.2F, .5F, growthProgress);
                float progress = emojiFadeIn.set(isEmojiLoaded) * growthProgress;
                StarGiftPatterns.drawRadialPattern(canvas, emoji, width / 2F, attractorY, (baseHeight + snapGrowths[1]) / 2F, alpha, progress);
            } else {
                emojiFadeIn.set(isEmojiLoaded);
            }

            // Gift pattern
            if (!gifts.isEmpty() && growthProgress > 0F) {
                float progress = hasGiftsAnimated.set(true) * growthProgress;
                StarGiftPatterns.drawRadialPattern(canvas, gifts, width / 2F, attractorY, (baseHeight + snapGrowths[1]) / 2F, -1F, progress);
            } else {
                hasGiftsAnimated.set(false);
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
