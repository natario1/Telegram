package org.telegram.ui.Profile;

import android.content.Context;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class ProfileCoordinatorLayout extends FrameLayout implements NestedScrollingParent3 {

    public abstract static class Header extends FrameLayout {
        protected int growth = 0;
        protected int maxGrowth = 0;
        protected int[] snapGrowths = new int[0];
        protected int baseHeight = 0;

        public Header(@NonNull Context context) {
            super(context);
        }

        public final boolean canGrow() {
            return growth < maxGrowth;
        }

        public final int getGrowth() {
            return growth;
        }

        public final void configureHeights(int baseHeight) {
            if (baseHeight >= 0) this.baseHeight = baseHeight;
            if (getParent() != null) getParent().requestLayout();
        }

        public final void configureGrowth(int maxGrowth, int[] snapGrowths) {
            if (maxGrowth >= 0) this.maxGrowth = maxGrowth;
            if (snapGrowths != null) this.snapGrowths = snapGrowths;
            if (getParent() != null) getParent().requestLayout();
        }

        private void applyGrowth(int growth) {
            this.growth = Math.max(Math.min(growth, maxGrowth), 0);
            setTranslationY(this.growth - maxGrowth);
            onGrowthChanged(this.growth);
        }

        public final void changeGrowth(int targetGrowth, boolean animated) {
            ProfileCoordinatorLayout parent = (ProfileCoordinatorLayout) getParent();
            if (parent != null) {
                parent.changeContentOffset(targetGrowth, animated, false);
            } else {
                applyGrowth(targetGrowth);
            }
        }

        protected void onGrowthChanged(int growth) {
            // No-op
        }

        protected int onContentTouch(int dy) {
            return dy;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(baseHeight + maxGrowth, MeasureSpec.EXACTLY));
        }
    }

    private Header header;
    private RecyclerView content;

    private final boolean[] hasNestedScroll = new boolean[2];
    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            int contentOffset = Math.max(0, getContentOffset());
            header.applyGrowth(contentOffset);
        }
    };

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
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (content != null) {
            ((LayoutParams) content.getLayoutParams()).topMargin = header.baseHeight;
            if (content.getPaddingTop() != header.maxGrowth) {
                // Called only once
                content.setPadding(content.getPaddingLeft(), header.maxGrowth, content.getPaddingRight(), content.getPaddingBottom());
                LinearLayoutManager manager = (LinearLayoutManager) content.getLayoutManager();
                if (manager != null) manager.scrollToPositionWithOffset(0, -header.maxGrowth + header.growth);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return !hasNestedScroll[type] && target == content && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        hasNestedScroll[type] = true;
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        if (type == ViewCompat.TYPE_NON_TOUCH || !hasNestedScroll[ViewCompat.TYPE_NON_TOUCH]) {
            snapContentOffset(); // If hasNestedScroll[TYPE_NON_TOUCH], there'll be a fling animation that we didn't block
        }
        hasNestedScroll[type] = false;
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
        if (type == ViewCompat.TYPE_TOUCH) {
            int actualDy = header.onContentTouch(dy);
            consumed[1] = dy - actualDy;
        }
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        // If fling is not that fast, abort the default recycler fling and snap ourselves
        // If it's fast enough, we'll snap after the default fling ends
        return Math.abs(velocityY / AndroidUtilities.density) < 1000 && snapContentOffset();
    }

    @Override
    public int getNestedScrollAxes() {
        return hasNestedScroll[ViewCompat.TYPE_TOUCH] || hasNestedScroll[ViewCompat.TYPE_NON_TOUCH] ? ViewCompat.SCROLL_AXIS_VERTICAL : ViewCompat.SCROLL_AXIS_NONE;
    }

    private int getContentOffset() {
        if (content == null) return Integer.MIN_VALUE;
        RecyclerView.LayoutManager manager = content.getLayoutManager();
        View view = manager == null ? null : manager.findViewByPosition(0);
        return view != null ? view.getTop() : Integer.MIN_VALUE;
    }

    private boolean snapContentOffset() {
        if (content == null || header.snapGrowths.length == 0) return false;
        int distance = header.growth - header.snapGrowths[0];
        boolean last = false;
        for (int s = 1; s < header.snapGrowths.length; s++) {
            int delta = header.growth - header.snapGrowths[s];
            if (Math.abs(delta) < Math.abs(distance)) {
                distance = delta;
                last = s == header.snapGrowths.length - 1;
            }
        }
        boolean overscroll = last && distance > 0; // snap to legit position faster in this case
        return changeContentOffset(header.growth - distance, true, overscroll);
    }

    private boolean changeContentOffset(int targetGrowth, boolean animated, boolean fast) {
        int coerced = Math.max(Math.min(targetGrowth, header.maxGrowth), 0);
        int distance = header.growth - coerced;
        if (distance == 0) return false;
        if (animated) {
            content.smoothScrollBy(0, distance, fast ? 100 : 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        } else {
            content.scrollBy(0, distance);
        }
        return true;
    }
}
