package org.telegram.ui.Profile;

import android.content.Context;
import android.graphics.*;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.function.Consumer;

import static org.telegram.messenger.AndroidUtilities.*;
import static org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT;

public class ProfileTextsView extends FrameLayout {

    private static final float COLLAPSED_LEFT = dpf2(72);
    private static final float COLLAPSED_TITLE_TOP = dpf2(8.33F);
    private static final float COLLAPSED_SUBTITLE_TOP = dpf2(31.67F);

    private static final float EXPANDED_LEFT = dpf2(20.33F);
    private static final float EXPANDED_TITLE_BOTTOM = dpf2(101F);
    private static final float EXPANDED_SUBTITLE_BOTTOM = dpf2(80.33F);

    private static final float MID_TITLE_BOTTOM = dpf2(106.67F);
    private static final float MID_SUBTITLE_BOTTOM = dpf2(87.33F);

    private static final float[] X_INSETS = new float[] { COLLAPSED_LEFT, EXPANDED_LEFT, EXPANDED_LEFT };
    private static final float[] X_ALIGNMENTS = new float[] { 0F, 0.5F, 0F };

    private static final float[] TITLE_SCALES = new float[] { 1F, 1.19F, 1.35F };

    private final SimpleTextView title;
    private final SimpleTextView smallSubtitle;
    private final SimpleTextView buttonSubtitle;
    private final Paint buttonSubtitlePaint = new Paint();
    private final AudioPlayerAlert.ClippingTextViewSwitcher mediaCounterSubtitle;
    private final float[] results = new float[3];

    private float progress = -1F;
    private int baseHeight;
    private int currentGrowth;
    private final Theme.ResourcesProvider resourcesProvider;
    private MessagesController.PeerColor lastPeerColor;
    private float lastActionModeProgress;
    private boolean lastIsOnline;
    private final ActionBar actionBar;
    private final long dialogId;
    private final int currentAccount;
    private final boolean isTopic;
    private boolean hasButton;

    private Drawable lockDrawable;
    private ScamDrawable scamDrawable;
    private CrossfadeDrawable verificationDrawable;
    private CrossfadeDrawable premiumDrawable;
    private ShowDrawable showDrawable;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botVerificationDrawable;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiStatusDrawable;
    private Long emojiStatusGiftId;

    private String rightDrawableContentDescription = null;
    private String rightDrawable2ContentDescription = null;

    ProfileTextsView(Context context, Theme.ResourcesProvider resourcesProvider, ActionBar actionBar, long dialogId, int currentAccount, boolean isTopic) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.actionBar = actionBar;
        this.dialogId = dialogId;
        this.isTopic = isTopic;
        this.currentAccount = currentAccount;

        this.title = new SimpleTextView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (isFocusable() && (rightDrawableContentDescription != null || rightDrawable2ContentDescription != null)) {
                    StringBuilder s = new StringBuilder(getText());
                    if (rightDrawable2ContentDescription != null) {
                        if (s.length() > 0) s.append(", ");
                        s.append(rightDrawable2ContentDescription);
                    }
                    if (rightDrawableContentDescription != null) {
                        if (s.length() > 0) s.append(", ");
                        s.append(rightDrawableContentDescription);
                    }
                    info.setText(s);
                }
            }
        };
        title.setWidthWrapContent(true);
        title.setGravity(Gravity.LEFT);
        title.setTypeface(AndroidUtilities.bold());
        title.setScrollNonFitText(true);
        title.setEllipsizeByGradient(true);
        title.setFocusable(false);
        title.setRightDrawableOutside(true);
        title.setTextSize(18);

        smallSubtitle = new SimpleTextView(context);
        smallSubtitle.setWidthWrapContent(true);
        smallSubtitle.setRightDrawableOutside(true);
        smallSubtitle.setFocusable(false);
        smallSubtitle.setGravity(Gravity.LEFT);
        smallSubtitle.setClickable(false);
        smallSubtitle.setTextSize(14);

        buttonSubtitle = new SimpleTextView(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, getHeight()/2F, getHeight()/2F, buttonSubtitlePaint);
                super.onDraw(canvas);
            }
        };
        buttonSubtitle.setPadding(dp(10F), dp(4F), dp(6F), dp(4F));
        buttonSubtitle.setTextColor(Color.WHITE);
        buttonSubtitle.setTypeface(AndroidUtilities.bold());
        buttonSubtitle.setWidthWrapContent(true);
        buttonSubtitle.setFocusable(false);
        buttonSubtitle.setGravity(Gravity.LEFT);
        buttonSubtitle.setClickable(false);
        buttonSubtitle.setTextSize(14);

        mediaCounterSubtitle = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                TextView textView = new TextView(context);
                textView.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, AndroidUtilities.dp(14));
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setGravity(Gravity.LEFT);
                return textView;
            }
        };

        addView(title, LayoutHelper.createFrame(WRAP_CONTENT, WRAP_CONTENT));
        addView(smallSubtitle, LayoutHelper.createFrame(WRAP_CONTENT, WRAP_CONTENT));
        addView(buttonSubtitle, LayoutHelper.createFrame(WRAP_CONTENT, WRAP_CONTENT));
        addView(mediaCounterSubtitle, LayoutHelper.createFrame(WRAP_CONTENT, WRAP_CONTENT));
        adjustLayout();
        adjustColors();
    }

    void updateLayout(int base, int growth, float attractorProgress, float fullscreenProgress) {
        if (fullscreenProgress > 0F) {
            progress = fullscreenProgress;
        } else {
            progress = attractorProgress - 1F;
        }
        this.baseHeight = base;
        this.currentGrowth = growth;
        adjustLayout();
        adjustColors();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (botVerificationDrawable != null) botVerificationDrawable.attach();
        if (emojiStatusDrawable != null) emojiStatusDrawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (botVerificationDrawable != null) botVerificationDrawable.detach();
        if (emojiStatusDrawable != null) emojiStatusDrawable.detach();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        adjustLayout();
    }

    private float interpolate(float[] values) {
        if (progress >= 0) return lerp(values[1], values[2], progress);
        return lerp(values[0], values[1], 1F + progress);
    }

    private void adjustLayout() {
        // Scale
        float titleScale = interpolate(TITLE_SCALES);
        title.setPivotY(0F);
        title.setPivotX(0F);
        title.setScaleX(titleScale);
        title.setScaleY(titleScale);

        // YS
        int statusBarY = actionBar.getOccupyStatusBar() ? statusBarHeight : 0;
        results[0] = baseHeight - statusBarY - COLLAPSED_TITLE_TOP;
        results[1] = MID_TITLE_BOTTOM + title.getMeasuredHeight() * titleScale + (isTopic ? dp(20) : 0);
        results[2] = EXPANDED_TITLE_BOTTOM + title.getMeasuredHeight() * titleScale;
        title.setTranslationY(getMeasuredHeight() - interpolate(results));
        results[0] = baseHeight - statusBarY - COLLAPSED_SUBTITLE_TOP;
        results[1] = MID_SUBTITLE_BOTTOM + smallSubtitle.getMeasuredHeight() + (isTopic ? dp(8) : 0);
        results[2] = EXPANDED_SUBTITLE_BOTTOM + smallSubtitle.getMeasuredHeight();
        smallSubtitle.setTranslationY(getMeasuredHeight() - interpolate(results));
        results[0] = baseHeight - statusBarY - COLLAPSED_SUBTITLE_TOP;
        results[1] = MID_SUBTITLE_BOTTOM + buttonSubtitle.getMeasuredHeight() + (isTopic ? dp(8) : 0);
        results[2] = EXPANDED_SUBTITLE_BOTTOM + buttonSubtitle.getMeasuredHeight();
        buttonSubtitle.setTranslationY(getMeasuredHeight() - interpolate(results));

        // XS
        float insetX = interpolate(X_INSETS);
        float alignment = Utilities.clamp(interpolate(X_ALIGNMENTS), 1F, 0F);
        float room = Math.max(0F, getMeasuredWidth() - insetX - insetX);
        title.setTranslationX(insetX + lerp(0, room - title.getMeasuredWidth() * titleScale, alignment));
        smallSubtitle.setTranslationX(insetX + lerp(0, room - smallSubtitle.getMeasuredWidth(), alignment));
        buttonSubtitle.setTranslationX(insetX + lerp(0, room - buttonSubtitle.getMeasuredWidth(), alignment));

        // Media counter
        mediaCounterSubtitle.setTranslationY(getMeasuredHeight() - currentGrowth - baseHeight + statusBarY + COLLAPSED_SUBTITLE_TOP);
        mediaCounterSubtitle.setTranslationX(X_INSETS[0]);

        // Re-layout long text
        LayoutParams titleParams = (LayoutParams) title.getLayoutParams();
        int titleUnboundedWidth = title.getTextWidth() + title.getSideDrawablesSize();
        int titleScaledRoom = (int) (room / titleScale);
        int titleNewWidth = titleUnboundedWidth > titleScaledRoom ? titleScaledRoom : WRAP_CONTENT;
        if (titleParams.width != titleNewWidth) {
            titleParams.width = titleNewWidth;
            title.setLayoutParams(titleParams);
            if (title.isInLayout()) title.post(title::requestLayout);
        }

        smallSubtitle.setVisibility(hasButton && progress >= 0F ? View.GONE : View.VISIBLE);
        buttonSubtitle.setVisibility(hasButton && progress >= 0F ? View.VISIBLE : View.GONE);
    }

    public CharSequence getTitle() {
        return this.title.getText();
    }

    public void updateTitle(CharSequence text) {
        try {
            text = Emoji.replaceEmoji(text, title.getPaint().getFontMetricsInt(), false);
        } catch (Exception ignore) {
        }
        this.title.setText(text);
        adjustLayout();
    }

    public void updateTitleLeftDrawable(TLRPC.User user, TLRPC.Chat chat, boolean hasEncryptedChat) {
        title.setLeftDrawableTopPadding(0);
        if (user != null && hasEncryptedChat) {
            title.setLeftDrawableOutside(true);
            title.setLeftDrawable(ensureLockDrawable());
            title.setLeftDrawableTopPadding(dp(-1));
        } else if (user != null && user.bot_verification_icon != 0) {
            title.setLeftDrawableOutside(true);
            title.setLeftDrawable(ensureBotVerificationDrawable(user.bot_verification_icon));
        } else if (chat != null && chat.bot_verification_icon != 0) {
            title.setLeftDrawableOutside(true);
            title.setLeftDrawable(ensureBotVerificationDrawable(chat.bot_verification_icon));
        } else {
            title.setLeftDrawableOutside(false);
            title.setLeftDrawable(null);
        }
        adjustLayout();
        adjustColors();
    }

    public void updateTitleRightDrawable(TLRPC.User user, TLRPC.Chat chat, Consumer<TLRPC.User> clickHandler) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        title.setRightDrawableOnClick(null);

        if (user != null && !controller.premiumFeaturesBlocked() && !MessagesController.isSupportUser(user) && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0) {
            title.setRightDrawable(ensureEmojiStatusDrawable(user.emoji_status, true));
            title.setRightDrawableOnClick(v -> clickHandler.accept(user));
            rightDrawableContentDescription = LocaleController.getString(R.string.AccDescrPremium);
        } else if (user != null && controller.isPremiumUser(user)) {
            title.setRightDrawable(ensureEmojiStatusDrawable(null, true));
            title.setRightDrawableOnClick(v -> clickHandler.accept(user));
            rightDrawableContentDescription = LocaleController.getString(R.string.AccDescrPremium);
        } else if (chat != null && DialogObject.getEmojiStatusDocumentId(chat.emoji_status) != 0) {
            title.setRightDrawable(ensureEmojiStatusDrawable(chat.emoji_status, false));
            rightDrawableContentDescription = LocaleController.getString(R.string.AccDescrPremium);
        } else {
            title.setRightDrawable(null);
            rightDrawableContentDescription = null;
        }
        adjustLayout();
        adjustColors();
    }

    public void updateTitleRightDrawable2(TLRPC.User user, TLRPC.Chat chat) {
        boolean fake = user != null && user.fake || chat != null && chat.fake;
        boolean scam = user != null && user.scam || chat != null && chat.scam;
        boolean verified = user != null && user.verified || chat != null && chat.verified;
        rightDrawable2ContentDescription = null;
        if (fake) {
            title.setRightDrawable2(ensureScamDrawable(1));
            rightDrawable2ContentDescription = LocaleController.getString(R.string.FakeMessage);
        } else if (scam) {
            title.setRightDrawable2(ensureScamDrawable(0));
            rightDrawable2ContentDescription = LocaleController.getString(R.string.ScamMessage);
        } else if (verified) {
            title.setRightDrawable2(ensureVerificationDrawable());
            rightDrawable2ContentDescription = LocaleController.getString(R.string.AccDescrVerified);
        } else {
            title.setRightDrawable2(null);
        }
        adjustLayout();
        adjustColors();
    }

    public void updateSubtitle(CharSequence text, Runnable click) {
        smallSubtitle.setText(text);
        buttonSubtitle.setText(text);
        if (click != null) {
            smallSubtitle.setOnClickListener(v -> click.run());
            buttonSubtitle.setOnClickListener(v -> click.run());
        } else {
            smallSubtitle.setOnClickListener(null);
            buttonSubtitle.setOnClickListener(null);
        }
        hasButton = click != null;
        adjustLayout();
    }

    public void updateSubtitleRightDrawable(Runnable click) {
        if (click != null) {
            smallSubtitle.setDrawablePadding(dp(5));
            smallSubtitle.setRightDrawable(ensureShowDrawable());
            smallSubtitle.setRightDrawableOnClick(v -> click.run());
        } else {
            smallSubtitle.setDrawablePadding(0);
            smallSubtitle.setRightDrawable(null);
            smallSubtitle.setRightDrawableOnClick(null);
        }
        adjustLayout();
    }

    public void updateColors(MessagesController.PeerColor peerColor, float mediaHeaderProgress) {
        lastPeerColor = peerColor;
        lastActionModeProgress = mediaHeaderProgress;
        adjustColors();
    }

    public void updateOnlineStatus(boolean isOnline) {
        lastIsOnline = isOnline;
        adjustColors();
    }

    private void adjustColors() {
        smallSubtitle.setAlpha(1F - lastActionModeProgress);
        mediaCounterSubtitle.setAlpha(lastActionModeProgress);
        mediaCounterSubtitle.setVisibility(mediaCounterSubtitle.getAlpha() > 0F ? View.VISIBLE : View.GONE);

        int mediaHeaderColor = Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider); //  key_player_actionBarTitle
        int collapsedColor = lastPeerColor != null ? Color.WHITE : Theme.getColor(Theme.key_profile_title, resourcesProvider);
        int expandedColor = Color.WHITE;
        if (lastActionModeProgress > 0F) {
            title.setTextColor(ColorUtils.blendARGB(collapsedColor, mediaHeaderColor, lastActionModeProgress));
        } else {
            title.setTextColor(ColorUtils.blendARGB(collapsedColor, expandedColor, Utilities.clamp(progress, 1F, 0F)));
        }
        int overlayBackgroundColor = lastPeerColor != null ? lastPeerColor.getBgColor1(Theme.isCurrentThemeDark()) : Theme.getColor(Theme.key_avatar_backgroundActionBarBlue, resourcesProvider);
        overlayBackgroundColor = Theme.multAlpha(Theme.adaptHSV(overlayBackgroundColor, +0.18f, -0.15f), 0.5f);

        float expansion = Utilities.clamp(progress, 1F, 0F);

        if (lockDrawable != null) {
            int lockFrom = Theme.getColor(Theme.key_chat_lockIcon, resourcesProvider);
            int lockTo = lastActionModeProgress > 0 ? mediaHeaderColor : Color.WHITE;
            float lockProgress = lastActionModeProgress > 0 ? lastActionModeProgress : expansion;
            lockDrawable.setColorFilter(ColorUtils.blendARGB(lockFrom, lockTo, lockProgress), PorterDuff.Mode.MULTIPLY);
        }

        if (scamDrawable != null) {
            int scamFrom = Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider);
            int scamTo = Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider);
            scamDrawable.setColor(ColorUtils.blendARGB(scamFrom, scamTo, expansion));
        }

        if (botVerificationDrawable != null) {
            int botFrom = lastPeerColor != null
                    ? ColorUtils.blendARGB(lastPeerColor.getStoryColor1(Theme.isCurrentThemeDark()), 0xFFFFFFFF, 0.25f)
                    : Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider);
            int botTo = lastActionModeProgress > 0 ? mediaHeaderColor : 0x99ffffff;
            float botProgress = lastActionModeProgress > 0 ? lastActionModeProgress : expansion;
            botVerificationDrawable.setColor(ColorUtils.blendARGB(botFrom, botTo, botProgress));
        }

        if (emojiStatusDrawable != null) {
            int emojiStatusFrom = lastPeerColor != null
                    ? ColorUtils.blendARGB(lastPeerColor.getStoryColor1(Theme.isCurrentThemeDark()), 0xFFFFFFFF, 0.25f)
                    : Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider);
            int emojiStatusTo = lastActionModeProgress > 0 ? mediaHeaderColor : 0xffffffff;
            float emojiStatusProgress = lastActionModeProgress > 0 ? lastActionModeProgress : expansion;
            emojiStatusDrawable.setColor(ColorUtils.blendARGB(emojiStatusFrom, emojiStatusTo, emojiStatusProgress));
        }

        if (verificationDrawable != null) {
            verificationDrawable.setProgress(expansion);
            Drawable bg = ((CombinedDrawable) verificationDrawable.topDrawable).getBackgroundDrawable();
            Drawable fg = ((CombinedDrawable) verificationDrawable.topDrawable).getIcon();
            int bgStart = lastPeerColor != null
                    ? Theme.adaptHSV(lastPeerColor.getStoryColor1(Theme.isCurrentThemeDark()), +.1f, Theme.isCurrentThemeDark() ? -.1f : -.08f)
                    : Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider);
            bg.setColorFilter(ColorUtils.blendARGB(bgStart, Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider), lastActionModeProgress), PorterDuff.Mode.SRC_IN);
            fg.setColorFilter(ColorUtils.blendARGB(Color.WHITE, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), lastActionModeProgress), PorterDuff.Mode.SRC_IN);
        }

        if (showDrawable != null) {
            showDrawable.setAlpha2(1F - lastActionModeProgress);
            showDrawable.setTextColor(0x88FFFFFF);
            showDrawable.setBackgroundColor(ColorUtils.blendARGB(overlayBackgroundColor, 0x23ffffff, expansion));
        }

        if (premiumDrawable != null) {
            premiumDrawable.setProgress(expansion);
            Drawable star = premiumDrawable.topDrawable;
            star.setColorFilter(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider),
                    Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider),
                    lastActionModeProgress
            ), PorterDuff.Mode.MULTIPLY);
        }

        {
            int subtitleFrom = lastPeerColor != null
                    ? Theme.adaptHSV(lastPeerColor.getBgColor1(Theme.isCurrentThemeDark()), -.2f, +.2f)
                    : lastIsOnline ? Theme.getColor(Theme.key_profile_status, resourcesProvider)
                    : Theme.getColor(Theme.key_avatar_subtitleInProfileBlue, resourcesProvider);
            smallSubtitle.setTextColor(ColorUtils.blendARGB(subtitleFrom, 0xB3FFFFFF, expansion));
            buttonSubtitlePaint.setColor(overlayBackgroundColor);
            buttonSubtitle.invalidate();
        }
    }

    public void updateMediaCounter(SharedMediaLayout sharedMediaLayout, SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader, boolean isBot, int commonChats) {
        int id = sharedMediaLayout.getClosestTab();
        int[] mediaCount = sharedMediaPreloader.getLastMediaCount();
        if (id == SharedMediaLayout.TAB_PHOTOVIDEO) {
            if (mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY] <= 0 && mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY] <= 0) {
                if (mediaCount[MediaDataController.MEDIA_PHOTOVIDEO] <= 0) {
                    mediaCounterSubtitle.setText(LocaleController.getString(R.string.SharedMedia));
                } else {
                    mediaCounterSubtitle.setText(LocaleController.formatPluralString("Media", mediaCount[MediaDataController.MEDIA_PHOTOVIDEO]));
                }
            } else if (sharedMediaLayout.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_PHOTOS_ONLY || mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]));
            } else if (sharedMediaLayout.getPhotosVideosTypeFilter() == SharedMediaLayout.FILTER_VIDEOS_ONLY || mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]));
            } else {
                String str = String.format("%s, %s", LocaleController.formatPluralString("Photos", mediaCount[MediaDataController.MEDIA_PHOTOS_ONLY]), LocaleController.formatPluralString("Videos", mediaCount[MediaDataController.MEDIA_VIDEOS_ONLY]));
                mediaCounterSubtitle.setText(str);
            }
        } else if (id == SharedMediaLayout.TAB_FILES) {
            if (mediaCount[MediaDataController.MEDIA_FILE] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.getString(R.string.Files));
            } else {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("Files", mediaCount[MediaDataController.MEDIA_FILE]));
            }
        } else if (id == SharedMediaLayout.TAB_VOICE) {
            if (mediaCount[MediaDataController.MEDIA_AUDIO] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.getString(R.string.Voice));
            } else {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("Voice", mediaCount[MediaDataController.MEDIA_AUDIO]));
            }
        } else if (id == SharedMediaLayout.TAB_LINKS) {
            if (mediaCount[MediaDataController.MEDIA_URL] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.getString(R.string.SharedLinks));
            } else {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("Links", mediaCount[MediaDataController.MEDIA_URL]));
            }
        } else if (id == SharedMediaLayout.TAB_AUDIO) {
            if (mediaCount[MediaDataController.MEDIA_MUSIC] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.getString(R.string.Music));
            } else {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("MusicFiles", mediaCount[MediaDataController.MEDIA_MUSIC]));
            }
        } else if (id == SharedMediaLayout.TAB_GIF) {
            if (mediaCount[MediaDataController.MEDIA_GIF] <= 0) {
                mediaCounterSubtitle.setText(LocaleController.getString(R.string.AccDescrGIFs));
            } else {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("GIFs", mediaCount[MediaDataController.MEDIA_GIF]));
            }
        } else if (id == SharedMediaLayout.TAB_COMMON_GROUPS) {
            mediaCounterSubtitle.setText(LocaleController.formatPluralString("CommonGroups", commonChats));
        } else if (id == SharedMediaLayout.TAB_GROUPUSERS) {
            mediaCounterSubtitle.setText(smallSubtitle.getText());
        } else if (id == SharedMediaLayout.TAB_STORIES) {
            if (isBot) {
                mediaCounterSubtitle.setText(sharedMediaLayout.getBotPreviewsSubtitle(false));
            } else {
                mediaCounterSubtitle.setText(LocaleController.formatPluralString("ProfileStoriesCount", sharedMediaLayout.getStoriesCount(id)));
            }
        } else if (id == SharedMediaLayout.TAB_BOT_PREVIEWS) {
            mediaCounterSubtitle.setText(sharedMediaLayout.getBotPreviewsSubtitle(true));
        } else if (id == SharedMediaLayout.TAB_ARCHIVED_STORIES) {
            mediaCounterSubtitle.setText(LocaleController.formatPluralString("ProfileStoriesArchiveCount", sharedMediaLayout.getStoriesCount(id)));
        } else if (id == SharedMediaLayout.TAB_RECOMMENDED_CHANNELS) {
            final MessagesController.ChannelRecommendations rec = MessagesController.getInstance(currentAccount).getChannelRecommendations(dialogId);
            mediaCounterSubtitle.setText(LocaleController.formatPluralString(isBot ? "Bots" : "Channels", rec == null ? 0 : rec.chats.size() + rec.more));
        } else if (id == SharedMediaLayout.TAB_SAVED_MESSAGES) {
            int messagesCount = MessagesController.getInstance(currentAccount).getSavedMessagesController().getMessagesCount(dialogId);
            mediaCounterSubtitle.setText(LocaleController.formatPluralString("SavedMessagesCount", Math.max(1, messagesCount)));
        } else if (id == SharedMediaLayout.TAB_GIFTS) {
            mediaCounterSubtitle.setText(LocaleController.formatPluralStringComma("ProfileGiftsCount", sharedMediaLayout.giftsContainer == null ? 0 : sharedMediaLayout.giftsContainer.getGiftsCount()));
        }
    }

    private Drawable ensureLockDrawable() {
        if (lockDrawable != null) return lockDrawable;
        lockDrawable = Theme.chat_lockIconDrawable.getConstantState().newDrawable().mutate();
        return lockDrawable;
    }

    private Drawable ensureBotVerificationDrawable(long icon) {
        if (botVerificationDrawable == null) {
            botVerificationDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(title, AndroidUtilities.dp(17), AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD);
            if (isAttachedToWindow()) botVerificationDrawable.attach();
        }
        if (icon != 0) {
            botVerificationDrawable.set(icon, false);
        } else {
            botVerificationDrawable.set((Drawable) null, false);
        }
        return botVerificationDrawable;
    }

    private Drawable ensureScamDrawable(int type) {
        if (scamDrawable != null) {
            scamDrawable.setCurrentType(type);
            return scamDrawable;
        }
        scamDrawable = new ScamDrawable(11, type);
        return scamDrawable;
    }

    private Drawable ensureVerificationDrawable() {
        if (verificationDrawable != null) return verificationDrawable;
        Drawable bg = Theme.profile_verifiedDrawable.getConstantState().newDrawable().mutate();
        Drawable fg = Theme.profile_verifiedCheckDrawable.getConstantState().newDrawable().mutate();
        CombinedDrawable collapsed = new CombinedDrawable(bg, fg);
        verificationDrawable = new CrossfadeDrawable(collapsed, ContextCompat.getDrawable(getContext(), R.drawable.verified_profile));
        return verificationDrawable;
    }

    private Drawable ensureShowDrawable() {
        if (showDrawable != null) return showDrawable;
        showDrawable = new ShowDrawable(LocaleController.getString(R.string.StatusHiddenShow));
        return showDrawable;
    }

    private Drawable ensurePremiumDrawable() {
        if (premiumDrawable != null) return premiumDrawable;
        Drawable star1 = ContextCompat.getDrawable(getContext(), R.drawable.msg_premium_liststar).mutate();
        Drawable star2 = ContextCompat.getDrawable(getContext(), R.drawable.msg_premium_prolfilestar).mutate();
        star1.setColorFilter(Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY);
        premiumDrawable = new CrossfadeDrawable(star1, star2);
        return premiumDrawable;
    }

    private Drawable ensureEmojiStatusDrawable(TLRPC.EmojiStatus emojiStatus, boolean animated) {
        if (emojiStatusDrawable == null) {
            emojiStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(title, AndroidUtilities.dp(24), AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD);
            if (isAttachedToWindow()) emojiStatusDrawable.attach();
        }

        emojiStatusGiftId = null;
        if (emojiStatus instanceof TLRPC.TL_emojiStatus) {
            final TLRPC.TL_emojiStatus status = (TLRPC.TL_emojiStatus) emojiStatus;
            if ((status.flags & 1) == 0 || status.until > (int) (System.currentTimeMillis() / 1000)) {
                emojiStatusDrawable.set(status.document_id, animated);
                emojiStatusDrawable.setParticles(false, animated);
            } else {
                emojiStatusDrawable.set(ensurePremiumDrawable(), animated);
                emojiStatusDrawable.setParticles(false, animated);
            }
        } else if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) emojiStatus;
            if ((status.flags & 1) == 0 || status.until > (int) (System.currentTimeMillis() / 1000)) {
                emojiStatusGiftId = status.collectible_id;
                emojiStatusDrawable.set(status.document_id, animated);
                emojiStatusDrawable.setParticles(true, animated);
            } else {
                emojiStatusDrawable.set(ensurePremiumDrawable(), animated);
                emojiStatusDrawable.setParticles(false, animated);
            }
        } else {
            emojiStatusDrawable.set(ensurePremiumDrawable(), animated);
            emojiStatusDrawable.setParticles(false, animated);
        }
        return emojiStatusDrawable;
    }

    public void updateEmojiStatusSelection(TL_stars.TL_starGiftUnique gift, Long documentId, TLRPC.Chat chat) {
        emojiStatusGiftId = gift != null ? gift.id : null;
        if (emojiStatusDrawable != null) {
            if (documentId == null && chat == null) {
                emojiStatusDrawable.set(ensurePremiumDrawable(), true);
            } else if (documentId != null) {
                emojiStatusDrawable.set(documentId, true);
            } else {
                emojiStatusDrawable.set((Drawable) null, true);
            }
            emojiStatusDrawable.setParticles(gift != null, true);
        }
        adjustColors();
    }

    public void getEmojiStatusLocation(Rect rect, View ancestor) {
        if (title.getRightDrawable() == null) return;

        Rect bounds = title.getRightDrawable().getBounds();
        float width = bounds.width() * title.getScaleX();
        float height = bounds.height() * title.getScaleY();

        float px = bounds.left * title.getScaleX(), py = bounds.top * title.getScaleY();
        View v = title;
        while (v != ancestor && v != null) {
            px += v.getX();
            py += v.getY();
            v = (View) v.getParent();
        }
        rect.set((int) px, (int) py, (int) (px + width), (int) (py + height));
    }

    public void prepareEmojiSelectionPopup(SelectAnimatedEmojiDialog popupLayout) {
        if (emojiStatusGiftId != null) {
            popupLayout.setSelected(emojiStatusGiftId);
        } else {
            popupLayout.setSelected(emojiStatusDrawable != null && emojiStatusDrawable.getDrawable() instanceof AnimatedEmojiDrawable ? ((AnimatedEmojiDrawable) emojiStatusDrawable.getDrawable()).getDocumentId() : null);
        }
        popupLayout.setScrimDrawable(emojiStatusDrawable, title);
    }

    public void preparePremiumBottomSheet(PremiumPreviewBottomSheet premiumPreviewBottomSheet) {
        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable = emojiStatusDrawable;
        int[] coords = new int[2];
        title.getLocationInWindow(coords);
        premiumPreviewBottomSheet.startEnterFromX = title.rightDrawableX;
        premiumPreviewBottomSheet.startEnterFromY = title.rightDrawableY;
        premiumPreviewBottomSheet.startEnterFromScale = title.getScaleX();
        premiumPreviewBottomSheet.startEnterFromX1 = title.getX();
        premiumPreviewBottomSheet.startEnterFromY1 = title.getY();
        premiumPreviewBottomSheet.startEnterFromView = title;
        if (title.getRightDrawable() == drawable && drawable != null && drawable.getDrawable() instanceof AnimatedEmojiDrawable) {
            premiumPreviewBottomSheet.startEnterFromScale *= 0.98f;
            TLRPC.Document document = ((AnimatedEmojiDrawable) drawable.getDrawable()).getDocument();
            if (document != null) {
                BackupImageView icon = new BackupImageView(getContext());
                String filter = "160_160";
                ImageLocation mediaLocation;
                String mediaFilter;
                SvgHelper.SvgDrawable thumbDrawable = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if ("video/webm".equals(document.mime_type)) {
                    mediaLocation = ImageLocation.getForDocument(document);
                    mediaFilter = filter + "_" + ImageLoader.AUTOPLAY_FILTER;
                    if (thumbDrawable != null) {
                        thumbDrawable.overrideWidthAndHeight(512, 512);
                    }
                } else {
                    if (thumbDrawable != null && MessageObject.isAnimatedStickerDocument(document, false)) {
                        thumbDrawable.overrideWidthAndHeight(512, 512);
                    }
                    mediaLocation = ImageLocation.getForDocument(document);
                    mediaFilter = filter;
                }
                icon.setLayerNum(7);
                icon.setRoundRadius(AndroidUtilities.dp(4));
                icon.setImage(mediaLocation, mediaFilter, ImageLocation.getForDocument(thumb, document), "140_140", thumbDrawable, document);
                if (((AnimatedEmojiDrawable) drawable.getDrawable()).canOverrideColor()) {
                    icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
                    premiumPreviewBottomSheet.statusStickerSet = MessageObject.getInputStickerSet(document);
                } else {
                    premiumPreviewBottomSheet.statusStickerSet = MessageObject.getInputStickerSet(document);
                }
                premiumPreviewBottomSheet.overrideTitleIcon = icon;
                premiumPreviewBottomSheet.isEmojiStatus = true;
            }
        }
    }
}
