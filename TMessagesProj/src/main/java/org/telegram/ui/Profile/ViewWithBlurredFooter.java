package org.telegram.ui.Profile;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.*;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.viewpager.widget.ViewPager;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ProfileGalleryView;

import java.util.HashMap;
import java.util.Map;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Components.LayoutHelper.*;

/**
 * Lays out a square view and a blurred strip below it, which blurs the bottom part of the square view.
 * Used to wrap both the avatar and the gallery viewpager in the {@link ProfileHeaderView} hierarchy.
 */
class ViewWithBlurredFooter extends FrameLayout {

    private static final boolean DYNAMIC_BLUR_ALLOWED = true;
    private static final int FOOTER_GRADIENT_HEIGHT = dp(32);
    private static final int FOOTER_BLURRED_HEIGHT = dp(73.33F);
    private static final int FOOTER_HEIGHT = FOOTER_BLURRED_HEIGHT + FOOTER_GRADIENT_HEIGHT;
    private static final int FOOTER_ROOM = ProfileHeaderView.EXTRA_HEIGHT_FOOTER;

    private final View content;
    final ViewClipper clipper;
    private final Blur footer;
    private int bottomRoom;
    private float externalRoundInset;
    private float lastProgress;

    public ViewWithBlurredFooter(View content, ProfileGalleryView gallery, Theme.ResourcesProvider resourcesProvider) {
        super(gallery.getContext());
        clipper = new ViewClipper(this) {
            @Override
            public void setInset(float inset) {
                externalRoundInset = inset;
                updateInsets();
            }
        };
        this.content = content;
        this.footer = Blur.create(getContext(), gallery, resourcesProvider);
        addView(content, createFrame(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP));
        addView(footer, createFrame(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM));
        setFooterVisibility(0F);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        content.measure(widthMeasureSpec, heightMeasureSpec);
        int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
        int thisWidth = content.getMeasuredWidth();
        float layoutFactor = (float) thisWidth / fullWidth;
        bottomRoom = (int) Math.ceil(layoutFactor * FOOTER_ROOM); // final h may be a few pixels taller
        int footerHeight = (int) (layoutFactor * FOOTER_HEIGHT);
        footer.measure(makeMeasureSpec(thisWidth, EXACTLY), makeMeasureSpec(footerHeight, EXACTLY));
        setMeasuredDimension(content.getMeasuredWidth(), content.getMeasuredHeight() + bottomRoom);
        setPivotY(thisWidth / 2F);
        setPivotX(thisWidth / 2F);
        updateInsets();
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

    public void setFooterVisibility(float progress) {
        lastProgress = progress;
        footer.setAlpha(Math.min(1F, progress*3));
        updateInsets();
        if (progress == 0F || progress == 1F) footer.redraw(bottomRoom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        footer.redraw(bottomRoom);
    }

    private void updateInsets() {
        float layoutBottomInset = lerp(bottomRoom, 0F, lastProgress);
        clipper.setInsets(externalRoundInset, externalRoundInset, externalRoundInset, externalRoundInset + layoutBottomInset);
    }

    public float translateContentCenter(float target) {
        float staticCenter = getTop() + getWidth()/2F;
        setTranslationY(target - staticCenter);
        return target - staticCenter;
    }

    private static abstract class Blur extends View {

        private static Blur create(Context context, ProfileGalleryView gallery, Theme.ResourcesProvider resourcesProvider) {
            if (DYNAMIC_BLUR_ALLOWED && SharedConfig.canBlurChat() && SharedConfig.useNewBlur && Build.VERSION.SDK_INT >= 31) {
                return new DynamicBlur(context, gallery, resourcesProvider);
            }
            return new StaticBlur(context, gallery, resourcesProvider);
        }

        private final Paint fallbackPaint = new Paint();
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix maskMatrix = new Matrix();

        private Blur(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            LinearGradient maskGradient = new LinearGradient(0, 0, 0, 1F,
                    new int[] { Color.TRANSPARENT, Color.BLACK },
                    new float[] { 0, (float) FOOTER_GRADIENT_HEIGHT / FOOTER_HEIGHT },
                    Shader.TileMode.CLAMP
            );
            maskPaint.setShader(maskGradient);
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            fallbackPaint.setColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
        }

        protected abstract boolean canDrawContent(Canvas canvas);
        protected abstract void drawContent(Canvas canvas);
        protected abstract void redraw(float contentOffset);

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            maskMatrix.setScale(1F, h);
            maskPaint.getShader().setLocalMatrix(maskMatrix);
        }

        @Override
        protected final void onDraw(@NonNull Canvas canvas) {
            canvas.saveLayer(0F, 0F, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);
            if (canDrawContent(canvas)) {
                drawContent(canvas);
            } else {
                canvas.drawRect(0F, 0F, getWidth(), getHeight(), fallbackPaint);
            }
            canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);
            canvas.restore();
        }
    }

    private static class StaticBlur extends Blur {
        private static class Info {
            Bitmap bitmap;
            BitmapShader shader;
        }

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix bitmapMatrix = new Matrix();
        private final Map<Integer, Info> blurs = new HashMap<>();
        private final ProfileGalleryView gallery;
        private Info currentInfo;

        private StaticBlur(Context context, ProfileGalleryView gallery, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            this.gallery = gallery;

            gallery.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
                @Override public void onPageScrollStateChanged(int state) {}
                @Override public void onPageSelected(int position) { refresh(); }
            });
            gallery.getAdapter().registerDataSetObserver(new DataSetObserver() {
                @Override public void onChanged() { refresh(); }
            });
            refresh();
        }

        private void refresh() {
            BackupImageView imageView = gallery.getCurrentItemView();
            ImageReceiver receiver = imageView == null ? null : imageView.getImageReceiver();
            Bitmap bitmap = receiver == null ? null : receiver.getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                setData(null);
                if (isAttachedToWindow()) postDelayed(this::refresh, 500);
                return;
            }
            int key = System.identityHashCode(bitmap);
            Info cached = blurs.get(key);
            if (cached != null) {
                setData(cached);
                return;
            }
            Info result = new Info();
            result.bitmap = Utilities.stackBlurBitmapMax(bitmap);
            result.shader = new BitmapShader(result.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            blurs.put(key, result);
            setData(result);
        }

        private void setData(Info info) {
            if (info == currentInfo) return;
            bitmapPaint.setShader(info != null ? info.shader : null);
            currentInfo = info;
            invalidate();
        }

        @Override
        protected void redraw(float contentOffset) {
            invalidate();
        }

        @Override
        protected boolean canDrawContent(Canvas canvas) {
            return currentInfo != null;
        }

        @Override
        protected void drawContent(Canvas canvas) {
            bitmapMatrix.setScale((float) getWidth() / currentInfo.bitmap.getWidth(), (float) getHeight() / currentInfo.bitmap.getHeight());
            bitmapMatrix.postScale(1F, (float) getWidth() / getHeight(), 0, getHeight());
            currentInfo.shader.setLocalMatrix(bitmapMatrix);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bitmapPaint);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            refresh();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            blurs.forEach((k, v) -> { v.bitmap.recycle(); });
            blurs.clear();
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static class DynamicBlur extends Blur {
        private final IntersectionBlurDrawable drawable;

        private DynamicBlur(Context context, ProfileGalleryView gallery, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            this.drawable = new IntersectionBlurDrawable(this, gallery);
            gallery.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { invalidate(); }
                @Override public void onPageSelected(int position) { invalidate(); }
                @Override public void onPageScrollStateChanged(int state) { invalidate(); }
            });
            gallery.getAdapter().registerDataSetObserver(new DataSetObserver() {
                @Override public void onChanged() { postInvalidate(); }
                @Override public void onInvalidated() { postInvalidate(); }
            });
        }

        @Override
        protected void redraw(float contentOffset) {
            drawable.setOffset(0, (int) -contentOffset);
            invalidate();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            drawable.setDirty();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            drawable.setBounds(0, 0, w, h);
        }

        @Override
        protected boolean canDrawContent(Canvas canvas) {
            return canvas.isHardwareAccelerated();
        }

        @Override
        protected void drawContent(Canvas canvas) {
            drawable.draw(canvas);
        }
    }
}
