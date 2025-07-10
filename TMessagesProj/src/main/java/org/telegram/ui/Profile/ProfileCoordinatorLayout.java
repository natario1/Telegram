package org.telegram.ui.Profile;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
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

        public final int getGrowth() {
            return growth;
        }

        public final void configureHeights(int baseHeight) {
            if (baseHeight >= 0) this.baseHeight = baseHeight;
            requestLayout();
        }

        public final void configureGrowth(int maxGrowth, int[] snapGrowths) {
            if (maxGrowth >= 0) this.maxGrowth = maxGrowth;
            applyGrowth(growth);
            requestLayout();
        }

        private void applyGrowth(int growth) {
            int last = this.growth;
            this.growth = Math.max(Math.min(growth, maxGrowth), 0);
            setTranslationY(this.growth - maxGrowth);
            ProfileCoordinatorLayout parent = (ProfileCoordinatorLayout) getParent();
            onGrowthChanged(this.growth, this.growth - last, parent != null ? parent.lastVelocity : 0F);
        }

        public final void changeGrowth(int targetGrowth, boolean animated) {
            ProfileCoordinatorLayout parent = (ProfileCoordinatorLayout) getParent();
            if (parent != null) {
                parent.changeContentOffset(targetGrowth, animated, false);
            } else {
                applyGrowth(targetGrowth);
            }
        }

        protected void onGrowthChanged(int growth, int change, float velocity) {
            // No-op
        }

        protected int onContentScroll(int dy, boolean touch) {
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

    private VelocityTracker velocityTracker;
    private float lastVelocity;

    private float interceptInitialX;
    private float interceptInitialY;
    private int interceptState = 0;
    private final Rect interceptRect = new Rect();
    private int interceptSlop = -1;

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
                if (manager != null) manager.scrollToPositionWithOffset(0, header.growth - content.getPaddingTop());
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
        int actualDy = header.onContentScroll(dy, type == ViewCompat.TYPE_TOUCH);
        consumed[1] = dy - actualDy;
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        // If fling is not that fast, abort the default recycler fling and snap ourselves
        // If it's fast enough, we'll snap after the default fling ends
        return Math.abs(velocityY / AndroidUtilities.density) < 2400 && snapContentOffset();
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
        boolean strong = Math.abs(lastVelocity / AndroidUtilities.density) > 700;
        if (!strong) {
            int index = 0;
            int minDelta = Integer.MAX_VALUE;
            for (int i = 0; i < header.snapGrowths.length; i++) {
                int delta = Math.abs(header.growth - header.snapGrowths[i]);
                if (delta < minDelta) {
                    minDelta = delta;
                    index = i;
                }
            }
            return snapContentOffset(index);
        } else if (lastVelocity > 0F) {
            for (int i = 0; i < header.snapGrowths.length; i++) {
                if (header.snapGrowths[i] >= header.growth) {
                    return snapContentOffset(i);
                }
            }
            return snapContentOffset(header.snapGrowths.length - 1);
        } else {
            for (int i = header.snapGrowths.length - 1; i >= 0; i--) {
                if (header.snapGrowths[i] <= header.growth) {
                    return snapContentOffset(i);
                }
            }
            return snapContentOffset(0);
        }
    }

    private boolean snapContentOffset(int index) {
        boolean overscroll = index == header.snapGrowths.length - 1 && header.growth > header.snapGrowths[index]; // snap to legit position faster in this case
        return changeContentOffset(header.snapGrowths[index], true, overscroll);
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

    private void computeVelocity(MotionEvent ev) {
        final int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            } else {
                velocityTracker.clear();
            }
            velocityTracker.addMovement(ev);
            lastVelocity = 0F;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);
                lastVelocity = velocityTracker.getYVelocity(ev.getPointerId(ev.getActionIndex()));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
                lastVelocity = 0F;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        computeVelocity(ev);
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            header.getHitRect(interceptRect);
            if (interceptRect.contains((int) ev.getX(), (int) ev.getY())) {
                interceptInitialX = ev.getX();
                interceptInitialY = ev.getY();
            } else {
                interceptInitialX = Float.NaN;
                interceptInitialY = Float.NaN;
            }
            interceptState = 0;
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (interceptState == 0 && !Float.isNaN(interceptInitialX)) {
                float dx = Math.abs(ev.getX() - interceptInitialX);
                float dy = Math.abs(ev.getY() - interceptInitialY);
                if (interceptSlop == -1) {
                    interceptSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                }
                if (dy > interceptSlop && dy > dx) {
                    interceptState = 1;
                }
            }
        }

        return interceptState > 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (interceptState == 0) return super.onTouchEvent(ev);

        computeVelocity(ev);
        float offsetX = -content.getLeft();
        float offsetY = -content.getTop();

        if (interceptState == 1) {
            MotionEvent down = MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(), MotionEvent.ACTION_DOWN, interceptInitialX, interceptInitialY, ev.getMetaState());
            down.offsetLocation(offsetX, offsetY);
            boolean res = content.dispatchTouchEvent(down);
            down.recycle();
            interceptState = res ? 2 : 0;
        }

        if (interceptState > 0) {
            ev.offsetLocation(offsetX, offsetY);
            boolean res = content.dispatchTouchEvent(ev);
            ev.offsetLocation(-offsetX, -offsetY);
            interceptState = res ? interceptState : 0;
        }

        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            interceptState = 0;
        }
        return interceptState > 0;
    }

    /**
     * We use Recycler's paddingTop to make room for the header, but when items are dynamically inserted or removed,
     * RecyclerView doesn't preserve the topmost item position, preferring to place items inside the padded area.
     * To address this, one can use this layout manager and wrap changes with saveTop and restoreTop.
     */
    public static class TopPreservingLayoutManager extends LinearLayoutManager {
        private int pendingAnchorPosition = RecyclerView.NO_POSITION;
        private int pendingAnchorTop = 0;
        private final RecyclerView owner;

        public TopPreservingLayoutManager(Context context, RecyclerView owner) {
            super(context);
            this.owner = owner;
            setOrientation(LinearLayoutManager.VERTICAL);
            mIgnoreTopPadding = false;
        }

        public void saveTop() {
            View debug = findViewByPosition(0);
            pendingAnchorPosition = debug != null ? 0 : RecyclerView.NO_POSITION;
            pendingAnchorTop = debug != null ? debug.getTop() : 0;
        }

        public void restoreTop() {
            if (pendingAnchorPosition == RecyclerView.NO_POSITION) return;
            scrollToPositionWithOffset(pendingAnchorPosition, pendingAnchorTop - owner.getPaddingTop());
            pendingAnchorPosition = RecyclerView.NO_POSITION;
        }
    }
}
