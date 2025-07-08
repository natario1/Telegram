package org.telegram.ui.Profile;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SharedMediaLayout;

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
        title.setRightDrawableOutside(false);
        title.setTextSize(18);

        smallSubtitle = new SimpleTextView(context);
        smallSubtitle.setWidthWrapContent(true);
        smallSubtitle.setFocusable(false);
        smallSubtitle.setGravity(Gravity.LEFT);
        smallSubtitle.setClickable(false);
        smallSubtitle.setTextSize(14);

        buttonSubtitle = new SimpleTextView(context) {
            private final Paint paint = new Paint();
            {
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
                paint.setColor(Color.rgb(218, 218, 218));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, getHeight()/2F, getHeight()/2F, paint);
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

        // Theme.key_actionBarDefaultSubtitle, Theme.key_chat_status, AvatarDrawable.getProfileTextColorForId...
        int collapsedSubColor = Theme.getColor(lastIsOnline && lastPeerColor == null ? Theme.key_profile_status : Theme.key_avatar_subtitleInProfileBlue, resourcesProvider);
        int expandedSubColor = 0xB3FFFFFF;
        smallSubtitle.setTextColor(ColorUtils.blendARGB(collapsedSubColor, expandedSubColor, Utilities.clamp(progress, 1F, 0F)));
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
}
