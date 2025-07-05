package org.telegram.ui.Profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.*;

import java.util.ArrayList;

/**
 * Root view for ProfileActivity. Draws the action bar and other overlays.
 */
@SuppressLint("ViewConstructor")
public class ProfileActivityRootLayout extends SizeNotifierFrameLayout {
    /**
     * @noinspection deprecation
     */
    public final UndoView undoView;
    public final View blurredView;

    private AnimatorSet scrimAnimatorSet;
    private View scrimView;
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ActionBar actionBar;
    private final Paint actionBarBackButtonBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Theme.ResourcesProvider resourceProvider;

    public ProfileActivityRootLayout(Context context, Theme.ResourcesProvider resourceProvider, ActionBar actionBar) {
        super(context);
        setWillNotDraw(false);
        this.resourceProvider = resourceProvider;

        // Action bar
        this.actionBar = actionBar;
        this.actionBarBackButtonBackgroundPaint.setColor(getColor(Theme.key_listSelector));

        //noinspection deprecation
        undoView = new UndoView(context, null, false, resourceProvider);
        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                ProfileActivityRootLayout.this.invalidate(); // Is this really needed?
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int color = getColor(Theme.key_windowBackgroundWhite);
            blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(color, 100)));
        }
        blurredView.setFocusable(false);
        blurredView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blurredView.setVisibility(View.GONE);
        blurredView.setFitsSystemWindows(true);
        scrimPaint.setAlpha(0);
    }

    public void addDecorationViews() {
        if (actionBar.getParent() != null) {
            ((ViewGroup) actionBar.getParent()).removeView(actionBar);
        }
        addView(actionBar);
        addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void prepareBlurredView() {
        int w = (int) (getMeasuredWidth() / 6.0f);
        int h = (int) (getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }

    public void animateBlurredView(boolean isOpen, float progress) {
        if (blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setAlpha(isOpen ? 1.0f - progress : progress);
        }
    }

    public void hideBlurredView() {
        if (blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
    }

    public void hideUndoView(int animatedFlag) {
        undoView.hide(true, animatedFlag);
    }

    public void dimBehindView(float value, View view) {
        if (view != null) scrimView = view;
        boolean enable = value > 0;
        invalidate();
        if (scrimAnimatorSet != null) scrimAnimatorSet.cancel();
        scrimAnimatorSet = new AnimatorSet();
        final float startValue = enable ? 0 : scrimPaint.getAlpha() / 255F;
        final float endValue = enable ? value : 0F;
        ValueAnimator scrimPaintAlphaAnimator = ValueAnimator.ofFloat(startValue, endValue);
        scrimPaintAlphaAnimator.addUpdateListener(a -> {
            scrimPaint.setAlpha((int) (255 * (float) a.getAnimatedValue()));
            invalidate();
        });
        scrimAnimatorSet.playTogether(scrimPaintAlphaAnimator);
        scrimAnimatorSet.setDuration(enable ? 150 : 220);
        if (!enable) {
            scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scrimView = null;
                    invalidate();
                }
            });
        }
        scrimAnimatorSet.start();
    }

    private int getColor(int key) {
        return Theme.getColor(key, resourceProvider);
    }

    public void updateColors(MessagesController.PeerColor peerColor, float actionModeProgress, boolean fullscreen) {
        int rawBackground = peerColor != null || fullscreen ? Theme.ACTION_BAR_WHITE_SELECTOR_COLOR : getColor(Theme.key_avatar_actionBarSelectorBlue);
        int rawForeground = peerColor != null || fullscreen ? Color.WHITE : getColor(Theme.key_actionBarDefaultIcon);
        int foreground = ColorUtils.blendARGB(rawForeground, getColor(Theme.key_actionBarActionModeDefaultIcon), actionModeProgress);
        actionBar.setItemsBackgroundColor(ColorUtils.blendARGB(rawBackground, getColor(Theme.key_actionBarActionModeDefaultSelector), actionModeProgress), false);
        actionBar.setItemsColor(foreground, false);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == blurredView) return true;
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (scrimPaint.getAlpha() > 0) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        }
        if (scrimView != null) {
            int c = canvas.save();
            canvas.translate(scrimView.getLeft(), scrimView.getTop());
            if (scrimView == actionBar.getBackButton()) {
                int r = Math.max(scrimView.getMeasuredWidth(), scrimView.getMeasuredHeight()) / 2;
                int wasAlpha = actionBarBackButtonBackgroundPaint.getAlpha();
                actionBarBackButtonBackgroundPaint.setAlpha((int) (wasAlpha * (scrimPaint.getAlpha() / 255f) / 0.3f));
                canvas.drawCircle(r, r, r * 0.7f, actionBarBackButtonBackgroundPaint);
                actionBarBackButtonBackgroundPaint.setAlpha(wasAlpha);
            }
            scrimView.draw(canvas);
            canvas.restoreToCount(c);
        }
        if (blurredView.getVisibility() == View.VISIBLE) {
            if (blurredView.getAlpha() != 1f && blurredView.getAlpha() != 0) {
                canvas.saveLayerAlpha(blurredView.getLeft(), blurredView.getTop(), blurredView.getRight(), blurredView.getBottom(), (int) (255 * blurredView.getAlpha()), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(blurredView.getLeft(), blurredView.getTop());
            blurredView.draw(canvas);
            canvas.restore();
        }
    }

    /** @noinspection deprecation*/
    public void getThemeDescriptions(ArrayList<ThemeDescription> descriptions) {
        descriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        descriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
        descriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
        descriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        descriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        descriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        descriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));
    }

    public int addBanFromGroupView(Runnable onClick) {
        View view = new BanFromGroupView(getContext(), resourceProvider);
        view.setOnClickListener(v -> onClick.run());
        addView(view);
        return BanFromGroupView.HEIGHT;
    }

    private static class BanFromGroupView extends FrameLayout {
        private final static int HEIGHT = 48;

        private BanFromGroupView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setWillNotDraw(false);

            TextView textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setText(LocaleController.getString(R.string.BanFromTheGroup));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 1, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(HEIGHT, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
            Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
            Theme.chat_composeShadowDrawable.draw(canvas);
            canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
        }
    }
}
