package org.telegram.ui.Profile;

import android.content.Context;
import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AutoDeletePopupWrapper;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TimerDrawable;


public class ProfileActivityMenus extends ActionBar.ActionBarMenuOnItemClick {
    public final static int AB_MAIN_ID = 10;
    public final static int AB_EDIT_ID = 41;
    public final static int AB_EDIT_INFO_ID = 30;
    public final static int AB_ADD_PHOTO_ID = 36;

    private final ActionBarMenuItem mainMenuItem;
    private final ActionBarMenuItem editMenuItem;

    private final ImageView ttlIndicator;
    private AutoDeletePopupWrapper ttlPopupWrapper;
    private TimerDrawable ttlTimerDrawable;

    private final Theme.ResourcesProvider resourceProvider;
    private final Context context;

    public ProfileActivityMenus(Context context, Theme.ResourcesProvider resourceProvider, ActionBar actionBar) {
        this.resourceProvider = resourceProvider;
        this.context = context;

        ActionBarMenu menu = actionBar.createMenu();
        menu.removeAllViews();
        editMenuItem = menu.addItem(AB_EDIT_ID, R.drawable.group_edit_profile, resourceProvider);
        editMenuItem.setContentDescription(LocaleController.getString(R.string.Edit));
        mainMenuItem = menu.addItem(AB_MAIN_ID, R.drawable.ic_ab_other, resourceProvider);
        mainMenuItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        ttlIndicator = new ImageView(context);
        ttlIndicator.setImageResource(R.drawable.msg_mini_autodelete_timer);
        mainMenuItem.addView(ttlIndicator, LayoutHelper.createFrame(12, 12, Gravity.CENTER_VERTICAL | Gravity.LEFT, 8, 2, 0, 0));
        updateTtlIndicator(false, false);
    }

    private int getColor(int key) {
        return Theme.getColor(key, resourceProvider);
    }

    public void updateColors(MessagesController.PeerColor peerColor, float actionModeProgress) {
        int rawForeground = peerColor != null ? Color.WHITE : getColor(Theme.key_actionBarDefaultIcon);
        int foreground = ColorUtils.blendARGB(rawForeground, getColor(Theme.key_actionBarActionModeDefaultIcon), actionModeProgress);
        mainMenuItem.setIconColor(rawForeground);
        ttlIndicator.setColorFilter(new PorterDuffColorFilter(foreground, PorterDuff.Mode.MULTIPLY));
        if (ttlPopupWrapper != null && ttlPopupWrapper.textView != null) {
            ttlPopupWrapper.textView.invalidate();
        }
    }

    public void updateTtlIndicator(boolean visible, boolean animated) {
        AndroidUtilities.updateViewVisibilityAnimated(ttlIndicator, visible, 0.8f, animated);
    }

    public void updateTtlPopup(int ttl) {
        if (ttlTimerDrawable != null) ttlTimerDrawable.setTime(ttl);
        if (ttlPopupWrapper != null) ttlPopupWrapper.updateItems(ttl);
    }

    public void updateEditItem(boolean visible, boolean animated) {
        if (visible && editMenuItem.getVisibility() != View.VISIBLE) {
            editMenuItem.setVisibility(View.VISIBLE);
            if (animated) {
                editMenuItem.setAlpha(0);
                editMenuItem.animate().alpha(1f).setDuration(150).start();
            }
        } else if (!visible && editMenuItem.getVisibility() != View.GONE) {
            editMenuItem.setVisibility(View.GONE);
        }
    }

    public void clearMainMenu() {
        mainMenuItem.removeAllSubItems();
    }

    public void appendEditInfoItem() {
        mainMenuItem.addSubItem(AB_EDIT_INFO_ID, R.drawable.msg_edit, LocaleController.getString(R.string.EditInfo));
    }

    public void appendPhotoItem() {
        mainMenuItem.addSubItem(AB_ADD_PHOTO_ID, R.drawable.msg_addphoto, LocaleController.getString(R.string.AddPhoto));
    }

    public void appendAutoDeleteItem(boolean allowExtendedHint, AutoDeletePopupWrapper.Callback callback) {
        ttlPopupWrapper = new AutoDeletePopupWrapper(context, mainMenuItem.getPopupLayout().getSwipeBack(), new AutoDeletePopupWrapper.Callback() {
            @Override
            public void dismiss() {
                mainMenuItem.toggleSubMenu();
            }

            @Override
            public void setAutoDeleteHistory(int time, int action) {
                callback.setAutoDeleteHistory(time, action);
            }

            @Override
            public void showGlobalAutoDeleteScreen() {
                callback.showGlobalAutoDeleteScreen();
                dismiss();
            }
        }, false, 0, resourceProvider);
        if (allowExtendedHint) {
            ttlPopupWrapper.allowExtendedHint(getColor(Theme.key_windowBackgroundWhiteBlueText));
        }
        ttlTimerDrawable = TimerDrawable.getTtlIcon(0);
        mainMenuItem.addSwipeBackItem(0, ttlTimerDrawable, LocaleController.getString(R.string.AutoDeletePopupTitle), ttlPopupWrapper.windowLayout);
        mainMenuItem.addColoredGap();
    }
}
