package org.telegram.ui.Profile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

public class ProfileContentView extends RecyclerListView implements StoriesListPlaceProvider.ClippedView {

    private final ActionBar actionBar;
    private final SharedMediaLayout sharedMediaLayout;

    public ProfileContentView(
            @NonNull ActionBar actionBar,
            SharedMediaLayout sharedMediaLayout,
            @NonNull NotificationCenter notificationCenter
            ) {
        super(actionBar.getContext());
        this.actionBar = actionBar;
        this.sharedMediaLayout = sharedMediaLayout;
        setVerticalScrollBarEnabled(false);
        setItemAnimator(new ItemAnimator(notificationCenter));
        setClipToPadding(false);
        setHideIfEmpty(false);
        setGlowColor(0);
        setAdapter(new DebugAdapter());
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (sharedMediaLayout != null) {
                    sharedMediaLayout.setPinnedToTop(sharedMediaLayout.getY() <= 0);
                    if (sharedMediaLayout.isAttachedToWindow()) {
                        sharedMediaLayout.setVisibleHeight(getMeasuredHeight() - sharedMediaLayout.getTop());
                    }
                }
            }
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (sharedMediaLayout != null) {
                    sharedMediaLayout.scrollingByUser = scrollingByUser;
                }
            }
        });
    }

    @Override
    public void updateClip(int[] clip) {
        clip[0] = actionBar.getMeasuredHeight();
        clip[1] = getMeasuredHeight() - getPaddingBottom();
    }

    @Override
    protected boolean canHighlightChildAt(View child, float x, float y) {
        return !(child instanceof AboutLinkCell);
    }

    @Override
    protected boolean allowSelectChildAtPosition(View child) {
        return child != sharedMediaLayout;
    }

    @Override
    protected void requestChildOnScreen(@NonNull View child, View focused) {

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (sharedMediaLayout != null) {
            if (sharedMediaLayout.canEditStories() && sharedMediaLayout.isActionModeShown() && sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_BOT_PREVIEWS) {
                return false;
            }
            if (sharedMediaLayout.canEditStories() && sharedMediaLayout.isActionModeShown() && sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_STORIES) {
                return false;
            }
            if (sharedMediaLayout.giftsContainer != null && sharedMediaLayout.giftsContainer.isReordering()) {
                return false;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // WIP: updateBottomButtonY();
    }

    private class ItemAnimator extends DefaultItemAnimator {

        private int animationIndex = -1;
        private final NotificationCenter notificationCenter;

        public ItemAnimator(@NonNull NotificationCenter notificationCenter) {
            this.notificationCenter = notificationCenter;
            setMoveDelay(0);
            setMoveDuration(320);
            setRemoveDuration(320);
            setAddDuration(320);
            setSupportsChangeAnimations(false);
            setDelayAnimations(false);
            setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        }


        @Override
        protected void onAllAnimationsDone() {
            super.onAllAnimationsDone();
            AndroidUtilities.runOnUIThread(() -> {
                notificationCenter.onAnimationFinish(animationIndex);
            });
        }

        @Override
        public void runPendingAnimations() {
            boolean removalsPending = !mPendingRemovals.isEmpty();
            boolean movesPending = !mPendingMoves.isEmpty();
            boolean changesPending = !mPendingChanges.isEmpty();
            boolean additionsPending = !mPendingAdditions.isEmpty();
            if (removalsPending || movesPending || additionsPending || changesPending) {
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                valueAnimator.addUpdateListener(valueAnimator1 -> invalidate());
                valueAnimator.setDuration(getMoveDuration());
                valueAnimator.start();
                animationIndex = notificationCenter.setAnimationInProgress(animationIndex, null);
            }
            super.runPendingAnimations();
        }

        @Override
        protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
            return 0;
        }

        @Override
        protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
            super.onMoveAnimationUpdate(holder);
            // WIP: updateBottomButtonY();
        }
    }


    public class DebugAdapter extends RecyclerView.Adapter<DebugAdapter.VH> {

        public class VH extends RecyclerView.ViewHolder {
            public VH(Context context) {
                super(new TextView(context));
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(parent.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TextView text = (TextView) holder.itemView;
            text.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            text.setTextColor(Color.BLACK);
            text.setPadding(0, 24, 0, 24);
            text.setText("Row Number: " + position);
        }

        @Override
        public int getItemCount() {
            return 100;
        }
    }
}
