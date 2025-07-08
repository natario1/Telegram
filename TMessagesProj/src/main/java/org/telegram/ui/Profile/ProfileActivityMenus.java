package org.telegram.ui.Profile;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;

import java.util.List;

import static org.telegram.messenger.AndroidUtilities.dp;


public class ProfileActivityMenus {

    public final static int AB_CONTACT_ADD_ID = 1;
    public final static int AB_CONTACT_SHARE_ID = 3;
    public final static int AB_CONTACT_EDIT_ID = 4;
    public final static int AB_CONTACT_DELETE_ID = 5;
    public final static int AB_GROUP_LEAVE_ID = 7;
    public final static int AB_SHARE_ID = 10;
    public final static int AB_SHORTCUT_ID = 14;
    public final static int AB_SEARCH_MEMBERS_ID = 17;
    public final static int AB_STATISTICS_ID = 19;
    public final static int AB_START_SECRET_CHAT_ID = 20;
    public final static int AB_VIEW_DISCUSSION_ID = 22;
    public final static int AB_DELETE_TOPIC_ID = 23;
    public final static int AB_REPORT_ID = 24;
    public final static int AB_EDIT_INFO_ID = 30;
    public final static int AB_LOGOUT_ID = 31;
    public final static int AB_ADD_PHOTO_ID = 36;
    public final static int AB_QR_ID = 37;
    public final static int AB_SEND_GIFTS_ID = 38;
    public final static int AB_CHANNEL_STORIES_ID = 39;
    public final static int AB_EDIT_COLOR_ID = 40;
    public final static int AB_EDIT_ID = 41;
    public final static int AB_PROFILE_COPY_LINK_ID = 42;
    public final static int AB_PROFILE_SET_USERNAME_ID = 43;
    public final static int AB_BOT_VIEW_PRIVACY_ID = 44;
    public final static int AB_BOT_BLOCK_ID = 50;
    public final static int AB_BOT_UNBLOCK_ID = 51;
    public final static int AB_USER_BLOCK_ID = 52;
    public final static int AB_USER_UNBLOCK_ID = 53;
    public final static int AB_MAIN_ID = 999;

    private final static boolean QR = false; // removed in new design

    @Nullable private ActionBarMenuItem mainMenuItem;

    @Nullable private ActionBarMenuItem editMenuItem;
    private boolean editMenuItemNeeded;

    @Nullable private ActionBarMenuItem qrMenuItem;
    private boolean qrMenuItemNeeded;
    private boolean qrMenuItemDisplayable;

    private boolean actionMode; // mirrors mediaHeaderVisible

    private final ImageView ttlIndicator;
    private AutoDeletePopupWrapper ttlPopupWrapper;
    private TimerDrawable ttlTimerDrawable;

    private final ActionBar actionBar;
    private final Theme.ResourcesProvider resourceProvider;
    private final Context context;

    public ProfileActivityMenus(
            Context context,
            Theme.ResourcesProvider resourceProvider,
            ActionBar actionBar
    ) {
        this.resourceProvider = resourceProvider;
        this.context = context;
        this.actionBar = actionBar;

        ttlIndicator = new ImageView(context);
        ttlIndicator.setImageResource(R.drawable.msg_mini_autodelete_timer);
    }

    public void deinitialize() {
        ActionBarMenu menu = actionBar.createMenu();
        menu.removeView(mainMenuItem);
        menu.removeView(qrMenuItem);
        menu.removeView(editMenuItem);
    }

    public void initialize() {
        ActionBarMenu menu = actionBar.createMenu();

        if (QR) {
            qrMenuItem = menu.addItem(AB_QR_ID, R.drawable.msg_qr_mini, resourceProvider);
            qrMenuItem.setContentDescription(LocaleController.getString(R.string.GetQRCode));
            updateQrItemVisibility(false, null);
        }

        editMenuItem = menu.addItem(AB_EDIT_ID, R.drawable.group_edit_profile, resourceProvider);
        editMenuItem.setContentDescription(LocaleController.getString(R.string.Edit));
        updateEditItemVisibility(false, null);

        mainMenuItem = menu.addItem(AB_MAIN_ID, R.drawable.ic_ab_other, resourceProvider);
        mainMenuItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        updateMainItemVisibility(false, null);

        mainMenuItem.addView(ttlIndicator, LayoutHelper.createFrame(12, 12, Gravity.CENTER_VERTICAL | Gravity.LEFT, 8, 2, 0, 0));
        updateTtlIndicator(false, false);
    }

    private int getColor(int key) {
        return Theme.getColor(key, resourceProvider);
    }

    public void updateColors(MessagesController.PeerColor peerColor, float actionModeProgress) {
        int rawForeground = peerColor != null ? Color.WHITE : getColor(Theme.key_actionBarDefaultIcon);
        int foreground = ColorUtils.blendARGB(rawForeground, getColor(Theme.key_actionBarActionModeDefaultIcon), actionModeProgress);
        if (mainMenuItem != null) mainMenuItem.setIconColor(rawForeground);
        if (editMenuItem != null) editMenuItem.setIconColor(rawForeground);
        if (qrMenuItem != null) qrMenuItem.setIconColor(rawForeground);
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

    public void setActionMode(boolean actionMode, SharedMediaLayout sharedMediaLayout, List<Animator> animators) {
        if (actionMode == this.actionMode) return;
        this.actionMode = actionMode;
        updateQrItemVisibility(animators != null, animators);
        updateEditItemVisibility(animators != null, animators);
        updateMainItemVisibility(animators != null, animators);

        View mediaSearch = sharedMediaLayout.getSearchItem(); // search
        View mediaMore = sharedMediaLayout.photoVideoOptionsItem; // dots
        View mediaAnimatable = sharedMediaLayout.getSearchOptionsItem(); // overlay that animates between the two

        boolean showsMore = sharedMediaLayout.isOptionsItemVisible();
        boolean showsSearch = !showsMore && sharedMediaLayout.isSearchItemVisible();
        boolean startsVisible = animators != null || actionMode;
        if (mediaSearch != null) {
            mediaSearch.setVisibility(startsVisible && showsSearch ? View.VISIBLE : View.INVISIBLE);
        }
        if (mediaMore != null) {
            mediaMore.setVisibility(startsVisible && showsMore ? View.VISIBLE : View.INVISIBLE);
        }
        if (actionMode && (showsMore || showsSearch)) {
            if (mediaAnimatable != null) mediaAnimatable.setVisibility(View.VISIBLE);
            if (showsMore) {
                // false then true prevents the default animation
                sharedMediaLayout.animateSearchToOptions(false, false);
                sharedMediaLayout.animateSearchToOptions(true, false);
            } else {
                // Quickly go to 'more', then animate to 'search'
                sharedMediaLayout.animateSearchToOptions(true, false);
                sharedMediaLayout.animateSearchToOptions(false, animators != null);
            }
        } else {
            if (mediaAnimatable != null) mediaAnimatable.setVisibility(View.GONE);
        }

        if (animators != null) {
            if (mediaSearch != null && showsSearch) {
                animators.add(ObjectAnimator.ofFloat(mediaSearch, View.ALPHA, actionMode ? 1.0f : 0.0f));
                animators.add(ObjectAnimator.ofFloat(mediaSearch, View.TRANSLATION_Y, actionMode ? 0.0f : dp(10)));
            }
            if (mediaMore != null && showsMore) {
                animators.add(ObjectAnimator.ofFloat(sharedMediaLayout.photoVideoOptionsItem, View.ALPHA, actionMode ? 1.0f : 0.0f));
                animators.add(ObjectAnimator.ofFloat(sharedMediaLayout.photoVideoOptionsItem, View.TRANSLATION_Y, actionMode ? 0.0f : dp(10)));
            }
            ValueAnimator anim = ValueAnimator.ofFloat(0F, 1F);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (actionMode) return;
                    if (showsMore && mediaMore != null) mediaMore.setVisibility(View.INVISIBLE);
                    if (showsSearch && mediaSearch != null) mediaSearch.setVisibility(View.INVISIBLE);
                }
            });
            animators.add(anim);

        }
    }

    private static Animator.AnimatorListener hideOnEnd(View view) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        };
    }

    private static void updateItemVisibility(@Nullable View item, boolean visible, boolean animated, List<Animator> animators) {
        if (item == null) return;
        float alpha = visible ? 1F : 0F;
        float scale = visible ? 1F : 0F;
        item.animate().setListener(null).cancel();
        item.setClickable(visible);
        if (!animated) {
            item.setVisibility(visible ? View.VISIBLE : View.GONE);
            item.setTranslationY(0F);
            item.setScaleY(scale);
            item.setAlpha(alpha);
        } else if (animators != null) {
            item.setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(item, View.TRANSLATION_Y, !visible ? -dp(10) : 0.0f));
            item.setScaleY(1F);
            animators.add(ObjectAnimator.ofFloat(item, View.ALPHA, alpha));
            if (!visible) animators.get(animators.size() - 1).addListener(hideOnEnd(item));
        } else {
            ViewPropertyAnimator animator = item.animate();
            item.setVisibility(View.VISIBLE);
            animator.translationY(0F);
            animator.scaleY(scale);
            animator.alpha(alpha);
            animator.setInterpolator(visible ? new DecelerateInterpolator() : new AccelerateInterpolator());
            animator.setDuration(150).setListener(visible ? null : hideOnEnd(item)).start();
        }
    }

    private void updateMainItemVisibility(boolean animated, List<Animator> animators) {
        boolean isVisible = !actionMode;
        updateItemVisibility(mainMenuItem, isVisible, animated, animators);
    }

    public void setQrItemNeeded(boolean needed, boolean animated) {
        if (needed == qrMenuItemNeeded) return;
        qrMenuItemNeeded = needed;
        updateQrItemVisibility(animated, null);
    }

    // WIP: searchTransitionProgress > .5f
    public void setQrItemDisplayable(boolean displayable, boolean animated) {
        if (displayable == qrMenuItemDisplayable) return;
        qrMenuItemDisplayable = displayable;
        updateQrItemVisibility(animated, null);
    }

    private void updateQrItemVisibility(boolean animated, List<Animator> animators) {
        boolean isVisible = qrMenuItemNeeded && qrMenuItemDisplayable && !actionMode;
        updateItemVisibility(qrMenuItem, isVisible, animated, animators);
    }

    public void setEditItemNeeded(boolean needed, boolean animated) {
        if (needed == editMenuItemNeeded) return;
        editMenuItemNeeded = needed;
        updateEditItemVisibility(animated, null);
    }

    private void updateEditItemVisibility(boolean animated, List<Animator> animators) {
        boolean isVisible = editMenuItemNeeded && !actionMode;
        updateItemVisibility(editMenuItem, isVisible, animated, animators);
    }

    public void updateEditColorItem(boolean isPremium) {
        if (mainMenuItem == null) return;
        ActionBarMenuSubItem item = (ActionBarMenuSubItem) mainMenuItem.getSubItem(AB_EDIT_COLOR_ID);
        if (item == null) return;
        if (isPremium) {
            item.setIcon(R.drawable.menu_profile_colors);
        } else {
            Drawable icon = ContextCompat.getDrawable(context, R.drawable.menu_profile_colors_locked);
            icon.setColorFilter(new PorterDuffColorFilter(getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
            Drawable lockIcon = ContextCompat.getDrawable(context, R.drawable.msg_gallery_locked2);
            lockIcon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 0.5f), PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(icon, lockIcon, dp(1), -dp(1)) {
                @Override public void setColorFilter(ColorFilter colorFilter) {}
            };
            item.setIcon(combinedDrawable);
        }
    }

    public void updateStartSecretChatItem(TL_account.RequirementToContact requirementToContact) {
        // Starting a secret chat is only allowed if contact is not restricted, e.g., premium only
        if (mainMenuItem == null) return;
        mainMenuItem.setSubItemShown(AB_START_SECRET_CHAT_ID, DialogObject.isEmpty(requirementToContact));
    }

    public void updateUsernameRelatedItems(boolean hasUsername) {
        if (mainMenuItem == null) return;
        ActionBarMenuSubItem linkItem = (ActionBarMenuSubItem) mainMenuItem.getSubItem(AB_PROFILE_COPY_LINK_ID);
        ActionBarMenuSubItem usernameItem = (ActionBarMenuSubItem) mainMenuItem.getSubItem(AB_PROFILE_SET_USERNAME_ID);
        if (linkItem != null) {
            linkItem.setVisibility(hasUsername ? View.VISIBLE : View.GONE);
        }
        if (usernameItem != null) {
            int text = hasUsername ? R.string.ProfileUsernameEdit : R.string.ProfileUsernameSet;
            usernameItem.setIcon(hasUsername ? R.drawable.menu_username_change : R.drawable.menu_username_set);
            usernameItem.setText(LocaleController.getString(text));
        }
    }

    public void updateGalleryRelatedItems(ProfileGalleryView galleryView) {
        if (mainMenuItem == null) return;
        if (mainMenuItem.isSubMenuShowing()) {
            AndroidUtilities.runOnUIThread(() -> updateGalleryRelatedItems(galleryView), 500);
            return;
        }
        boolean first = galleryView.getRealPosition() == 0;
        mainMenuItem.setSubItemShown(AB_ADD_PHOTO_ID, first);
        // WIP: mainMenuItem.setSubItemShown(AB_SET_AS_MAIN_ID, !first);
    }

    public void clearMainMenu() {
        if (mainMenuItem == null) return;
        mainMenuItem.removeAllSubItems();
    }

    public void toggleMainMenuSubItem(int id, boolean visible) {
        if (mainMenuItem == null) return;
        mainMenuItem.setSubItemShown(id, visible);
    }

    public boolean hasMainMenuSubItem(int id) {
        return mainMenuItem != null && mainMenuItem.getSubItem(id) != null;
    }

    public void appendMainMenuSubItem(int id) {
        if (mainMenuItem == null) return;
        if (id == AB_CONTACT_SHARE_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_share, LocaleController.getString(R.string.ShareContact));
        } else if (id == AB_CONTACT_DELETE_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_delete, LocaleController.getString(R.string.DeleteContact));
        } else if (id == AB_CONTACT_EDIT_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_edit, LocaleController.getString(R.string.EditContact));
        } else if (id == AB_EDIT_INFO_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_edit, LocaleController.getString(R.string.EditInfo));
        } else if (id == AB_ADD_PHOTO_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_addphoto, LocaleController.getString(R.string.AddPhoto));
        } else if (id == AB_LOGOUT_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_leave, LocaleController.getString(R.string.LogOut));
        } else if (id == AB_EDIT_COLOR_ID) {
            mainMenuItem.addSubItem(id, R.drawable.menu_profile_colors, LocaleController.getString(R.string.ProfileColorEdit));
        } else if (id == AB_CONTACT_ADD_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_addcontact, LocaleController.getString(R.string.AddContact));
        } else if (id == AB_SHARE_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_share, LocaleController.getString(R.string.BotShare));
        } else if (id == AB_SHORTCUT_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_home, LocaleController.getString(R.string.AddShortcut));
        } else if (id == AB_STATISTICS_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_stats, LocaleController.getString(R.string.Statistics));
        } else if (id == AB_START_SECRET_CHAT_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_secret, LocaleController.getString(R.string.StartEncryptedChat));
        } else if (id == AB_PROFILE_COPY_LINK_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_link2, LocaleController.getString(R.string.ProfileCopyLink));
        } else if (id == AB_PROFILE_SET_USERNAME_ID) {
            mainMenuItem.addSubItem(id, R.drawable.menu_username_change, LocaleController.getString(R.string.ProfileUsernameEdit));
        } else if (id == AB_VIEW_DISCUSSION_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_discussion, LocaleController.getString(R.string.ViewDiscussion));
        } else if (id == AB_REPORT_ID) {
            ActionBarMenuSubItem item = mainMenuItem.addSubItem(id, R.drawable.msg_report, LocaleController.getString(R.string.ReportBot));
            item.setColors(getColor(Theme.key_text_RedRegular), getColor(Theme.key_text_RedRegular));
        } else if (id == AB_BOT_VIEW_PRIVACY_ID) {
            mainMenuItem.addSubItem(id, R.drawable.menu_privacy_policy, LocaleController.getString(R.string.BotPrivacyPolicy));
        } else if (id == AB_DELETE_TOPIC_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_delete, LocaleController.getPluralString("DeleteTopics", 1));
        } else if (id == AB_CHANNEL_STORIES_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_archive, LocaleController.getString(R.string.OpenChannelArchiveStories));
        } else if (id == AB_SEARCH_MEMBERS_ID) {
            mainMenuItem.addSubItem(id, R.drawable.msg_search, LocaleController.getString(R.string.SearchMembers));
        }
    }

    public void appendSendGiftsItem(boolean channel) {
        if (mainMenuItem == null) return;
        int text = channel ? R.string.ProfileSendAGiftToChannel : R.string.ProfileSendAGift;
        mainMenuItem.addSubItem(AB_SEND_GIFTS_ID, R.drawable.msg_gift_premium, LocaleController.getString(text));
    }

    public void appendBlockUnblockItem(boolean bot, boolean blocked) {
        if (mainMenuItem == null) return;
        if (bot) {
            if (blocked) {
                mainMenuItem.addSubItem(AB_BOT_UNBLOCK_ID, R.drawable.msg_retry, LocaleController.getString(R.string.BotRestart));
            } else {
                ActionBarMenuSubItem item = mainMenuItem.addSubItem(AB_BOT_BLOCK_ID, R.drawable.msg_block2, LocaleController.getString(R.string.DeleteAndBlock));
                item.setColors(getColor(Theme.key_text_RedRegular), getColor(Theme.key_text_RedRegular));
            }
        } else {
            int text = blocked ? R.string.Unblock : R.string.BlockContact;
            int id = blocked ? AB_USER_UNBLOCK_ID : AB_USER_BLOCK_ID;
            mainMenuItem.addSubItem(id, R.drawable.msg_block, LocaleController.getString(text));
        }
    }

    public void appendLeaveGroupItem(boolean megagroup, boolean channel) {
        if (mainMenuItem == null) return;
        int title = megagroup ? R.string.LeaveMegaMenu : channel ? R.string.LeaveChannelMenu : R.string.DeleteAndExit;
        mainMenuItem.addSubItem(AB_GROUP_LEAVE_ID, R.drawable.msg_leave, LocaleController.getString(title));
    }

    public void appendAutoDeleteItem(boolean allowExtendedHint, AutoDeletePopupWrapper.Callback callback) {
        if (mainMenuItem == null) return;
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
