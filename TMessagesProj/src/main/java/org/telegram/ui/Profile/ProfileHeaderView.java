package org.telegram.ui.Profile;

import android.graphics.Color;
import android.graphics.Point;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.util.ArrayList;

public class ProfileHeaderView extends ProfileCoordinatorLayout.Header {


    private final ActionBar actionBar;

    public ProfileHeaderView(@NonNull ActionBar actionBar) {
        super(actionBar.getContext());
        this.actionBar = actionBar;
    }

    @Override
    protected void onGrowthChanged(int growth) {
        super.onGrowthChanged(growth);
        int color = ColorUtils.blendARGB(Color.CYAN, Color.BLUE, (float) this.growth / maxGrowth);
        setBackgroundColor(color);
    }

    public void onConfigurationChanged(Point size) {
        int baseHeight = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        int overscrollHeight = AndroidUtilities.dp(48);
        int availableHeight = size.y - baseHeight;

        // Configure base height
        configureHeights(baseHeight);

        // Configure growth
        // WIP: also !isTablet and avatarImage.getImageReceiver().hasNotThumb()
        boolean landscape = size.x > size.y;
        boolean expandable = !landscape && !AndroidUtilities.isAccessibilityScreenReaderEnabled();
        if ((expandable && snapGrowths.length == 3) || (!expandable && snapGrowths.length == 2)) return;
        int mid = (int) Math.min(0.7F * availableHeight, AndroidUtilities.dp(246));
        if (expandable) {
            int max = Math.min(availableHeight - overscrollHeight, AndroidUtilities.dp(398));
            configureGrowth(max + overscrollHeight, new int[]{0, mid, max});
        } else {
            configureGrowth(mid, new int[]{0, mid});
        }

        // Animate to mid value
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

    public void getThemeDescriptions(ArrayList<ThemeDescription> arrayList) {
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue));
    }
}
