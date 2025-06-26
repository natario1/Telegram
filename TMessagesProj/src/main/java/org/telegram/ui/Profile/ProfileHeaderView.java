package org.telegram.ui.Profile;

import android.graphics.Color;
import android.graphics.Point;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;

public class ProfileHeaderView extends ProfileCoordinatorLayout.Header {

    private final static int DEFAULT_MID_GROWTH = AndroidUtilities.dp(300);
    private final static int DEFAULT_MAX_GROWTH = AndroidUtilities.dp(480);

    private final ActionBar actionBar;

    public ProfileHeaderView(@NonNull ActionBar actionBar) {
        super(actionBar.getContext());
        this.actionBar = actionBar;
    }

    @Override
    public int getBaseHeight() {
        return ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
    }

    @Override
    protected void onGrowthChanged(int growth) {
        super.onGrowthChanged(growth);
        int color = ColorUtils.blendARGB(Color.CYAN, Color.BLUE, (float) this.growth / maxGrowth);
        setBackgroundColor(color);
    }

    public void onConfigurationChanged(Point size) {
        // WIP: also isTablet and avatarImage.getImageReceiver().hasNotThumb()
        boolean landscape = size.x > size.y;
        boolean expandable = !landscape && !AndroidUtilities.isAccessibilityScreenReaderEnabled();
        boolean wasExpandable = snapGrowths.length == 3;
        if (expandable == wasExpandable && snapGrowths.length > 0) return;
        int availableHeight = size.y - getBaseHeight();
        int mid = (int) Math.min(0.7F * availableHeight, DEFAULT_MID_GROWTH);
        if (expandable) {
            int max = Math.min(availableHeight, DEFAULT_MAX_GROWTH);
            configureGrowth(max, new int[]{0, mid, max});
        } else {
            configureGrowth(mid, new int[]{0, mid});
        }
        changeGrowth(mid, true);
    }

    @Override
    protected int onContentTouch(int dy) {
        if (growth <= snapGrowths[1] || snapGrowths.length < 3) return dy;
        if (dy > 0) return dy; // no resistance when shrinking
        float progress = ((float) (growth - snapGrowths[1])) / (snapGrowths[2] - snapGrowths[1]);
        float factor;
        if (progress < 0.25F) { // slow down
            float t = progress / 0.25F;
            factor = AndroidUtilities.lerp(0.6F, 0.2F, t);
        } else { // accelerate
            float t = (progress - 0.25F) / 0.75F;
            factor = AndroidUtilities.lerp(4F, 0.6F, t);
        }
        return Math.round(dy * factor);
    }
}
