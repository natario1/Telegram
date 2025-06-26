package org.telegram.ui.Profile;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;

public class ProfileHeaderView extends ProfileCoordinatorLayout.Header {

    private final ActionBar actionBar;

    public ProfileHeaderView(@NonNull ActionBar actionBar) {
        super(
                actionBar.getContext(),
                AndroidUtilities.dp(480),
                new int[]{0, AndroidUtilities.dp(300), AndroidUtilities.dp(480)}
        );
        this.actionBar = actionBar;
        this.update();
    }

    @Override
    public int getBaseHeight() {
        return ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
    }

    @Override
    public void setGrowth(int growth) {
        super.setGrowth(growth);
        this.update();
    }

    private void update() {
        int color = ColorUtils.blendARGB(Color.CYAN, Color.BLUE, (float) growth / maxGrowth);
        setBackgroundColor(color);
    }
}
