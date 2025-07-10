package org.telegram.ui.Profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.viewpager.widget.ViewPager;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ProfileGalleryView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.telegram.messenger.AndroidUtilities.*;
import static org.telegram.messenger.Utilities.clamp;

public class ProfileActionsView extends LinearLayout {

    public interface OnActionClickListener {
        void onActionClick(Action action, View view);
    }

    public enum Action {
        JOIN(R.string.ProfileJoinShort, R.drawable.profile_actions_join),
        LEAVE(R.string.LeaveChannelOrGroup, R.drawable.profile_actions_leave), // can be shown in action bar too
        SHARE(R.string.BotShare, R.drawable.profile_actions_share), // can be shown in action bar too
        MUTE(R.string.ChatsMute, R.drawable.profile_actions_mute),
        UNMUTE(R.string.ChatsUnmute, R.drawable.profile_actions_unmute),
        REPORT(R.string.ReportChat, R.drawable.profile_actions_report), // can be shown in action bar too
        STOP(R.string.BizBotStop, R.drawable.profile_actions_block), // can be shown in action bar too, but this is specific for bots
        CALL(R.string.Call, R.drawable.profile_actions_call),
        VIDEO(R.string.GroupCallCreateVideo, R.drawable.profile_actions_video),
        GIFT(R.string.ActionStarGift, R.drawable.profile_actions_gift), // can be shown in action bar too
        STORY(R.string.AddStory, R.drawable.profile_actions_story),
        DISCUSS(R.string.ProfileDiscuss, R.drawable.profile_actions_message), // can be shown in action bar too
        MESSAGE(R.string.Message, R.drawable.profile_actions_message),
        LIVESTREAM(R.string.StartVoipChannelTitle, R.drawable.profile_actions_livestream),
        VOICECHAT(R.string.StartVoipChatTitle, R.drawable.profile_actions_livestream),
        ;

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
    private final static float ITEM_PADDING_TOP = dpf2(7.33F);
    private final static float ITEM_PADDING_BOTTOM = dpf2(9.67F);
    private final static int PADDING_Y = dp(12);
    private final static float ICON_SIZE = dpf2(24F);
    private final static float TEXT_SIZE = dpf2(11.2F); // 2.15

    private final Button[] buttons = new Button[4];
    private final List<Action> actions = new ArrayList<>();
    public OnActionClickListener listener;

    private final Map<Integer, Integer> galleryColors = new HashMap<>();
    private int galleryCurrentColor;
    private int lastSuggestedOverlayColor;
    private float lastFullscreenProgress;

    public ProfileActionsView(Context context, ProfileGalleryView gallery) {
        super(context);
        setOrientation(HORIZONTAL);
        int px = (int) (HORIZONTAL_INSET - ITEM_PADDING_X);
        setPadding(px, PADDING_Y, px, PADDING_Y);
        for (int i = 0; i < buttons.length; i++) {
            Button button = new Button(context, null);
            buttons[i] = button;
            button.setOnClickListener(v -> {
                if (listener == null || button.action == null) return;
                listener.onActionClick(button.action, v);
            });
            addView(button);
        }


        gallery.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
            @Override public void onPageScrollStateChanged(int state) {}
            @Override public void onPageSelected(int position) { extractGalleryColor(gallery); }
        });
        gallery.getAdapter().registerDataSetObserver(new DataSetObserver() {
            @Override public void onChanged() { extractGalleryColor(gallery); }
        });

        extractGalleryColor(gallery);
        setVisibleRoom(0F);
        adjustColors();
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1F);
    }

    public void editActions(Consumer<List<Action>> actions) {
        List<Action> list = new ArrayList<>(this.actions);
        actions.accept(list);
        this.actions.clear();
        for (int i = 0; i < buttons.length; i++) {
            Action action = i < list.size() ? list.get(i) : null;
            buttons[i].setAction(action);
            if (action != null) this.actions.add(action);
        }
    }

    public boolean containsAction(Action action) {
        return action != null && actions.contains(action);
    }

    void setVisibleRoom(float room) {
        float height = ITEM_HEIGHT + PADDING_Y * 2;
        float p0 = 0.1F * height;
        float p1 = 1.1F * height;
        float progress = clamp((room - p0) / (p1 - p0), 1, 0);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setProgress(progress);
        }
        setVisibility(progress > 0F ? View.VISIBLE : View.GONE);
    }

    private void extractGalleryColor(ProfileGalleryView gallery) {
        BackupImageView imageView = gallery.getCurrentItemView();
        ImageReceiver receiver = imageView == null ? null : imageView.getImageReceiver();
        Bitmap bitmap = receiver == null ? null : receiver.getBitmap();
        if (bitmap == null || bitmap.isRecycled()) {
            galleryCurrentColor = 0;
            if (isAttachedToWindow()) postDelayed(() -> extractGalleryColor(gallery), 500);
            return;
        }
        int key = System.identityHashCode(bitmap);
        Integer cached = galleryColors.get(key);
        if (cached != null) {
            galleryCurrentColor = cached;
        } else {
            galleryCurrentColor = AndroidUtilities.calcBitmapColor(bitmap);
            if (galleryCurrentColor != 0) galleryColors.put(key, galleryCurrentColor);
        }
        adjustColors();
    }

    void updateColors(MessagesController.PeerColor peerColor, int suggestedOverlayColor) {
        lastSuggestedOverlayColor = suggestedOverlayColor;
        adjustColors();
    }

    void updateFullscreen(float fullscreenProgress) {
        lastFullscreenProgress = fullscreenProgress;
        adjustColors();
    }

    private void adjustColors() {
        int expanded = 0x20000000;
        if (galleryCurrentColor != 0) {
            float bright = AndroidUtilities.computePerceivedBrightness(galleryCurrentColor);
            float change = MathUtils.clamp(bright, 0.1F, 0.3F) - bright;
            expanded = Theme.multAlpha(Theme.adaptHSV(galleryCurrentColor, 0.1F, change), 0.25F);
        }
        int result = ColorUtils.blendARGB(lastSuggestedOverlayColor, expanded, Math.min(1F, lastFullscreenProgress*lastFullscreenProgress));
        for (int b = 0; b < buttons.length; b++) {
            buttons[b].fallbackPaint.setColor(result);
            buttons[b].invalidate();
        }
    }

    private static class Button extends View {
        private final ViewClipper clipper = new ViewClipper(this);
        private final Paint fallbackPaint = new Paint();
        private float fallbackAlpha = 1F;
        private final BlurBehindDrawable blurDrawable;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";
        private Drawable icon;
        private float iconScale = 1F;
        private Action action;

        private Button(Context context, Action data) {
            super(context);
            clipper.setRoundRadius(dpf2(8.33F));
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

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            clipper.invalidate();
        }

        private void setAction(Action data) {
            this.action = data;
            if (data != null) {
                this.text = LocaleController.getString(data.textResId);
                this.icon = ContextCompat.getDrawable(getContext(), data.drawableResId);
                this.icon.setAlpha(textPaint.getAlpha());
                setVisibility(View.VISIBLE);
            } else {
                this.text = "";
                this.icon = null;
                setVisibility(View.GONE);
            }
            clipper.invalidate();
        }

        private void setProgress(float progress) {
            int alpha = (int) (0xFF * Math.max(0F, (progress - 0.4F) / 0.6F));
            textPaint.setAlpha(alpha);
            textPaint.setTextSize(lerp(0.6F * TEXT_SIZE, TEXT_SIZE, progress));
            if (icon != null) icon.setAlpha(alpha);
            iconScale = Math.max(0F, lerp(-0.6F, 1F, progress));
            fallbackAlpha = progress;
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
                int alpha = fallbackPaint.getAlpha();
                fallbackPaint.setAlpha((int) (alpha * fallbackAlpha));
                canvas.drawRect(0F, 0F, getWidth(), getHeight(), fallbackPaint);
                fallbackPaint.setAlpha(alpha);
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
