package org.telegram.ui.Profile;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class ProfileCoordinatorLayout extends FrameLayout implements NestedScrollingParent3 {

    public abstract static class Header extends FrameLayout {
        protected int growth;
        protected final int maxGrowth;
        protected final int[] snapGrowths;

        public Header(@NonNull Context context, int maxGrowth, @NonNull int[] snapGrowths) {
            super(context);
            this.maxGrowth = maxGrowth;
            this.snapGrowths = snapGrowths;
            this.growth = maxGrowth;
        }

        public abstract int getBaseHeight();

        public void setGrowth(int growth) {
            this.growth = growth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxHeight = getBaseHeight() + maxGrowth;
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM);
        }
    }

    private Header header;
    private RecyclerView content;

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            synchronizeHeader();
        }
    };

    private boolean hasTouchNestedScroll = false;
    private boolean hasNonTouchNestedScroll = false;

    public ProfileCoordinatorLayout(@NonNull Context context) {
        super(context);
        setChildrenDrawingOrderEnabled(true);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        return drawingPosition == 0 ? childCount - 1 : drawingPosition - 1;
    }

    public void addHeader(@NonNull Header header) {
        if (this.header == header) return;
        if (this.header != null) removeView(this.header);
        this.header = header;
        addView(header, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        requestLayout();

    }

    public void addContent(@NonNull RecyclerView content) {
        if (this.content == content) return;
        if (this.content != null) {
            removeView(this.content);
            this.content.removeOnScrollListener(scrollListener);
        }
        this.content = content;
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        content.addOnScrollListener(scrollListener);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (content != null) {
            ((LayoutParams) content.getLayoutParams()).topMargin = header.getBaseHeight();
            if (content.getPaddingTop() != header.maxGrowth) {
                // Called only once
                content.setPadding(content.getPaddingLeft(), header.maxGrowth, content.getPaddingRight(), content.getPaddingBottom());
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        boolean has = type == ViewCompat.TYPE_TOUCH ? hasTouchNestedScroll : hasNonTouchNestedScroll;
        return !has && target == content && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        if (type == ViewCompat.TYPE_TOUCH) hasTouchNestedScroll = true;
        else hasNonTouchNestedScroll = true;
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            snapContent();
            hasNonTouchNestedScroll = false;
        } else {
            // If hasNonTouchNestedScroll, there'll be a fling animation that we didn't block
            if (!hasNonTouchNestedScroll) snapContent();
            hasTouchNestedScroll = false;
        }
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        // No-op
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        // No-op
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        // No-op
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        // If fling is not that fast, abort the default recycler fling and snap ourselves
        // If it's fast enough, we'll snap after the default fling ends
        return Math.abs(velocityY) < 1000 && snapContent();
    }

    @Override
    public int getNestedScrollAxes() {
        if (hasNonTouchNestedScroll || hasTouchNestedScroll) return ViewCompat.SCROLL_AXIS_VERTICAL;
        return ViewCompat.SCROLL_AXIS_NONE;
    }

    private int getContentOffset() {
        if (content == null) return Integer.MIN_VALUE;
        RecyclerView.LayoutManager manager = content.getLayoutManager();
        View view = manager == null ? null : manager.findViewByPosition(0);
        return view != null ? view.getTop() : Integer.MIN_VALUE;
    }

    private boolean snapContent() {
        if (content == null || header.snapGrowths.length == 0) return false;
        int distance = header.growth - header.snapGrowths[0];
        for (int s = 1; s < header.snapGrowths.length; s++) {
            int delta = header.growth - header.snapGrowths[s];
            if (Math.abs(delta) < Math.abs(distance)) distance = delta;
        }
        if (distance == 0) return false;
        content.smoothScrollBy(0, distance, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        return true;
    }

    private void synchronizeHeader() {
        int contentOffset = Math.max(0, getContentOffset());
        if (contentOffset == header.growth) return;
        growHeaderBy(contentOffset - header.growth);
    }

    private void growHeaderBy(int delta) {
        header.setGrowth(Math.max(Math.min(header.growth + delta, header.maxGrowth), 0));
        header.setTranslationY(header.growth - header.maxGrowth);
    }
}
