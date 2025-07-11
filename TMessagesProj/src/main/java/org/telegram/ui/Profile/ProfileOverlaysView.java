package org.telegram.ui.Profile;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.ViewPager;
import org.telegram.messenger.*;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ProfileGalleryView;

import java.util.Arrays;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.ui.Components.LayoutHelper.createFrame;

/**
 * Overlays for {@link ProfileGalleryView}.
 * Adds shadow and gallery indicator (top bars or current page, depending on count).
 */
class ProfileOverlaysView extends FrameLayout {

    private final SimpleIndicator simpleIndicator;
    private final DetailedIndicator detailedIndicator;
    private final Shadows shadows;
    private final ViewClipper clipper;

    ProfileOverlaysView(Context context, ProfileGalleryView gallery, ViewClipper clipper) {
        super(context);
        this.clipper = clipper;
        this.simpleIndicator = new SimpleIndicator(context, gallery);
        this.detailedIndicator = new DetailedIndicator(context, gallery);
        this.shadows = new Shadows(context, gallery);

        addView(this.shadows, createFrame(MATCH_PARENT, MATCH_PARENT));
        addView(this.simpleIndicator, createFrame(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));
        addView(this.detailedIndicator, createFrame(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));

        setGalleryVisibility(0F, false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Size and pivot just like ViewWithBlurredFooter, with which we share a ViewClipper
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = w + ProfileHeaderView.EXTRA_HEIGHT_FOOTER;
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setPivotY(w / 2F);
        setPivotX(w / 2F);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (clipper.needsApply()) {
            canvas.save();
            clipper.apply(canvas);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    public void setGalleryVisibility(float progress) {
        setGalleryVisibility(progress, true);
    }

    private void setGalleryVisibility(float progress, boolean allowAnimations) {
        simpleIndicator.updateVisibility(progress == 1F, allowAnimations);
        detailedIndicator.updateVisibility(progress == 1F, allowAnimations);
        shadows.updateVisibility(progress);
    }

    public void setLayoutInsets(int statusBar, int bottomActionsBar) {
        simpleIndicator.setTranslationY(statusBar);
        detailedIndicator.setTranslationY(statusBar);
        shadows.setLayoutInsets(statusBar, bottomActionsBar);
    }

    private static class Shadows extends View {

        private final RadialGradient vignetteGradient = new RadialGradient(.5F, .5F, .5F, new int[]{ 0, ColorUtils.setAlphaComponent(Color.BLACK, 54) }, new float[]{ 0.7F, 1F }, Shader.TileMode.CLAMP);
        private final Matrix vignetteMatrix = new Matrix();
        private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int vignetteTop = 0;
        private int vignetteBottom = 0;
        private final GradientDrawable[] pressedGradients = new GradientDrawable[2];
        private final boolean[] pressedVisible = new boolean[2];
        private final float[] pressedAlpha = new float[2];
        private long lastDrawTime;

        public Shadows(Context context, ProfileGalleryView gallery) {
            super(context);
            vignettePaint.setShader(vignetteGradient);
            for (int i = 0; i < 2; i++) {
                final GradientDrawable.Orientation orientation = i == 0 ? GradientDrawable.Orientation.LEFT_RIGHT : GradientDrawable.Orientation.RIGHT_LEFT;
                pressedGradients[i] = new GradientDrawable(orientation, new int[]{0x32000000, 0});
                pressedGradients[i].setShape(GradientDrawable.RECTANGLE);
            }
            gallery.addCallback(new ProfileGalleryView.Callback() {
                @Override
                public void onDown(boolean left) {
                    pressedVisible[left ? 0 : 1] = true;
                    postInvalidateOnAnimation();
                }
                @Override
                public void onRelease() {
                    pressedVisible[0] = false;
                    pressedVisible[1] = false;
                    postInvalidateOnAnimation();
                }
            });
        }

        private void updateVisibility(float progress) {
            int alpha = (int) (0xFF * progress);
            vignettePaint.setAlpha(alpha);
            pressedGradients[0].setAlpha(alpha);
            pressedGradients[1].setAlpha(alpha);
            setVisibility(progress > 0F ? View.VISIBLE : View.INVISIBLE);
        }

        private void setLayoutInsets(int statusBar, int bottomActionsBar) {
            this.vignetteTop = 0;
            this.vignetteBottom = (int) (1F * bottomActionsBar);
            refreshRects();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            refreshRects();
        }

        private void refreshRects() {
            int w = getWidth();
            int h = getHeight();
            pressedGradients[0].setBounds(0, 0, w / 5, h);
            pressedGradients[1].setBounds(w - (w / 5), 0, w, h);

            float vw = w*1.35F;
            vignetteMatrix.setScale(vw, h - vignetteTop - vignetteBottom);
            vignetteMatrix.postTranslate(-(vw-w)/2F, vignetteTop);
            vignetteGradient.setLocalMatrix(vignetteMatrix);
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastDrawTime;
            if (dt < 0 || dt > 20) dt = 17;
            lastDrawTime = newTime;

            for (int i = 0; i < 2; i++) {
                if (pressedAlpha[i] > 0f) {
                    pressedGradients[i].setAlpha((int) (pressedAlpha[i] * 255));
                    pressedGradients[i].draw(canvas);
                }
            }
            canvas.drawRect(0, 0, getWidth(), getHeight(), vignettePaint);

            boolean invalidate = false;
            for (int i = 0; i < 2; i++) {
                if (pressedVisible[i] && pressedAlpha[i] < 1f) {
                    pressedAlpha[i] += dt / 180.0f;
                    if (pressedAlpha[i] > 1f) pressedAlpha[i] = 1f;
                    invalidate = true;
                } else if (!pressedVisible[i] && pressedAlpha[i] > 0f) {
                    pressedAlpha[i] -= dt / 180.0f;
                    if (pressedAlpha[i] < 0f) pressedAlpha[i] = 0f;
                    invalidate = true;
                }
            }
            if (invalidate) {
                postInvalidateOnAnimation();
            }
        }
    }

    private static class SimpleIndicator extends View {

        private String text = "";
        private final RectF rect = new RectF();
        private final TextPaint textPaint;
        private final Paint backgroundPaint;
        private final ProfileGalleryView gallery;
        private boolean possiblyVisible = true;

        public SimpleIndicator(Context context, ProfileGalleryView gallery) {
            super(context);
            this.gallery = gallery;
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.SANS_SERIF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(AndroidUtilities.dpf2(15f));
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(0x26000000);
            gallery.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
                @Override public void onPageScrollStateChanged(int state) {}
                @Override public void onPageSelected(int position) { refreshVisibility(true); }
            });
            gallery.getAdapter().registerDataSetObserver(new DataSetObserver() {
                @Override public void onChanged() { refreshVisibility(true); }
            });
        }

        private void updateVisibility(boolean visible, boolean animated) {
            possiblyVisible = visible;
            refreshVisibility(animated);
        }

        private void refreshVisibility(boolean animated) {
            boolean visible = possiblyVisible && gallery.getRealCount() >= DetailedIndicator.COUNT_LIMIT;
            AndroidUtilities.updateViewVisibilityAnimated(this, visible, 0F, true, animated);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(26));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            invalidate();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            text = gallery.getAdapter().getPageTitle(gallery.getCurrentItem()).toString();
            rect.set(0, 0, textPaint.measureText(text) + dpf2(16), getMeasuredHeight());
            rect.offset(getMeasuredWidth()/2F - rect.centerX(), 0F);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (text.isEmpty()) return;
            final float radius = dpf2(12);
            canvas.drawRoundRect(rect, radius, radius, backgroundPaint);
            canvas.drawText(text, rect.centerX(), rect.top + AndroidUtilities.dpf2(18.5f), textPaint);
        }
    }

    private static class DetailedIndicator extends View {
        private final static int COUNT_LIMIT = 20;

        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float[] itemAlphas = null;
        private float mainAlpha = 0F;
        private long lastDrawTime;
        private float previousSelectedProgress;
        private int previousSelectedPosition = -1;
        private float currentProgress;
        private int selectedPosition;
        private float currentLoadingAnimationProgress;
        private int currentLoadingAnimationDirection = 1;
        private final RectF rect = new RectF();
        private final ProfileGalleryView gallery;
        private int simpleIndicatorVisible;

        private DetailedIndicator(Context context, ProfileGalleryView gallery) {
            super(context);
            barPaint.setColor(0x55ffffff);
            selectedBarPaint.setColor(0xffffffff);
            gallery.addCallback(new ProfileGalleryView.Callback() {
                @Override public void onVideoSet() { invalidate(); }
            });
            gallery.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                private int lastPosition = -1;
                @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
                @Override public void onPageScrollStateChanged(int state) {}
                @Override
                public void onPageSelected(int position) {
                    int realPosition = gallery.getRealPosition(position);
                    if (realPosition != lastPosition) {
                        lastPosition = realPosition;
                        saveCurrentPageProgress();
                    }
                }
            });
            gallery.getAdapter().registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    int count = gallery.getRealCount();
                    if (simpleIndicatorVisible == 0 && count > 1 && count <= COUNT_LIMIT) {
                        simpleIndicatorVisible = 1;
                    }
                }
            });
            this.gallery = gallery;
        }

        private void updateVisibility(boolean visible, boolean animated) {
            AndroidUtilities.updateViewVisibilityAnimated(this, visible, 1F, animated);
        }

        private void saveCurrentPageProgress() {
            previousSelectedProgress = currentProgress;
            previousSelectedPosition = selectedPosition;
            currentLoadingAnimationProgress = 0.0f;
            currentLoadingAnimationDirection = 1;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastDrawTime;
            if (dt < 0 || dt > 20) dt = 17;
            lastDrawTime = newTime;

            int count = gallery.getRealCount();
            selectedPosition = gallery.getRealPosition();
            if (itemAlphas == null || itemAlphas.length != count) {
                itemAlphas = new float[count];
                Arrays.fill(itemAlphas, 0.0f);
            }

            boolean invalidate = false;
            if (count > 1 && count <= COUNT_LIMIT) {
                if (simpleIndicatorVisible == 0) {
                    mainAlpha = 0.0f;
                    simpleIndicatorVisible = 3;
                } else if (simpleIndicatorVisible == 1) {
                    mainAlpha = 0.0f;
                    simpleIndicatorVisible = 2;
                }
                if (simpleIndicatorVisible == 2) {
                    barPaint.setAlpha((int) (0x55 * mainAlpha));
                    selectedBarPaint.setAlpha((int) (0xff * mainAlpha));
                }
                int width = (getMeasuredWidth() - AndroidUtilities.dp(5 * 2) - AndroidUtilities.dp(2 * (count - 1))) / count;
                int y = 0;
                for (int a = 0; a < count; a++) {
                    int x = AndroidUtilities.dp(5 + a * 2) + width * a;
                    float progress;
                    int baseAlpha = 0x55;
                    if (a == previousSelectedPosition && Math.abs(previousSelectedProgress - 1.0f) > 0.0001f) {
                        progress = previousSelectedProgress;
                        canvas.save();
                        canvas.clipRect(x + width * progress, y, x + width, y + AndroidUtilities.dp(2));
                        rect.set(x, y, x + width, y + AndroidUtilities.dp(2));
                        barPaint.setAlpha((int) (0x55 * mainAlpha));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), barPaint);
                        baseAlpha = 0x50;
                        canvas.restore();
                        invalidate = true;
                    } else if (a == selectedPosition) {
                        if (gallery.isCurrentItemVideo()) {
                            progress = currentProgress = gallery.getCurrentItemProgress();
                            if (progress <= 0 && gallery.isLoadingCurrentVideo() || currentLoadingAnimationProgress > 0.0f) {
                                currentLoadingAnimationProgress += currentLoadingAnimationDirection * dt / 500.0f;
                                if (currentLoadingAnimationProgress > 1.0f) {
                                    currentLoadingAnimationProgress = 1.0f;
                                    currentLoadingAnimationDirection *= -1;
                                } else if (currentLoadingAnimationProgress <= 0) {
                                    currentLoadingAnimationProgress = 0.0f;
                                    currentLoadingAnimationDirection *= -1;
                                }
                            }
                            rect.set(x, y, x + width, y + AndroidUtilities.dp(2));
                            barPaint.setAlpha((int) ((0x55 + 0x30 * currentLoadingAnimationProgress) * mainAlpha));
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), barPaint);
                            invalidate = true;
                            baseAlpha = 0x50;
                        } else {
                            progress = currentProgress = 1.0f;
                        }
                    } else {
                        progress = 1.0f;
                    }
                    rect.set(x, y, x + width * progress, y + AndroidUtilities.dp(2));

                    if (a != selectedPosition) {
                        if (simpleIndicatorVisible == 3) {
                            barPaint.setAlpha((int) (AndroidUtilities.lerp(baseAlpha, 0xff, CubicBezierInterpolator.EASE_BOTH.getInterpolation(itemAlphas[a])) * mainAlpha));
                        }
                    } else {
                        itemAlphas[a] = 0.75f;
                    }

                    canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), a == selectedPosition ? selectedBarPaint : barPaint);
                }

                if (simpleIndicatorVisible == 2) {
                    if (mainAlpha < 1.0f) {
                        mainAlpha += dt / 180.0f;
                        if (mainAlpha > 1.0f) {
                            mainAlpha = 1.0f;
                        }
                        invalidate = true;
                    } else {
                        simpleIndicatorVisible = 3;
                    }
                } else if (simpleIndicatorVisible == 3) {
                    for (int i = 0; i < itemAlphas.length; i++) {
                        if (i != selectedPosition && itemAlphas[i] > 0.0f) {
                            itemAlphas[i] -= dt / 500.0f;
                            if (itemAlphas[i] <= 0.0f) {
                                itemAlphas[i] = 0.0f;
                                if (i == previousSelectedPosition) {
                                    previousSelectedPosition = -1;
                                }
                            }
                            invalidate = true;
                        } else if (i == previousSelectedPosition) {
                            previousSelectedPosition = -1;
                        }
                    }
                }
            }
            if (invalidate) {
                postInvalidateOnAnimation();
            }
        }
    }
}
