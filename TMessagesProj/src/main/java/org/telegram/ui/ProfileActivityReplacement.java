package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.ProfileChannelCell;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class ProfileActivityReplacement extends BaseFragment implements
        SharedMediaLayout.SharedMediaPreloaderDelegate,
        ImageUpdater.ImageUpdaterDelegate,
        NotificationCenter.NotificationCenterDelegate {

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private ImageUpdater imageUpdater;
    private FlagSecureReason flagSecure;

    private long chatId;
    private TLRPC.ChatFull chatInfo;
    private TLRPC.Chat chat;
    private TLRPC.EncryptedChat chatEncrypted;

    private long userId;
    private TLRPC.UserFull userInfo;
    private TL_account.TL_password userPassword;

    private MessagesController.PeerColor peerColor;
    private long topicId;
    private long dialogId;
    private boolean isSaved;
    private boolean isMyProfile;
    private boolean isOpen;

    public SharedMediaLayout sharedMediaLayout;
    private RootLayout rootLayout;

    private float mediaHeaderAnimationProgress = 0F;
    private boolean mediaHeaderVisible = false;

    public static ProfileActivityReplacement of(long dialogId) {
        Bundle bundle = new Bundle();
        if (dialogId >= 0) {
            bundle.putLong("user_id", dialogId);
        } else {
            bundle.putLong("chat_id", -dialogId);
        }
        return new ProfileActivityReplacement(bundle);
    }

    public ProfileActivityReplacement(Bundle args) {
        super(args);
    }

    // CONTENT

    // Called before presenting
    public void setSharedMediaPreloader(SharedMediaLayout.SharedMediaPreloader preloader) {
        sharedMediaPreloader = preloader;
    }

    // Called before presenting
    public void setPlayProfileAnimation(int type) {
        // WIP
    }

    // Called before presenting
    public void setFetchers(
        ProfileChannelCell.ChannelMessageFetcher channelMessageFetcher,
        ProfileBirthdayEffect.BirthdayEffectFetcher birthdayAssetsFetcher
    ) {
        // WIP
    }

    public void setUserInfo(TLRPC.UserFull value) {
        this.userInfo = value;
        if (sharedMediaLayout != null) {
            sharedMediaLayout.setUserInfo(value);
        }
        if (imageUpdater == null) {
            if (sharedMediaLayout != null) {
                sharedMediaLayout.setCommonGroupsCount(userInfo.common_chats_count);
            }
            if (sharedMediaPreloader == null || sharedMediaPreloader.isMediaWasLoaded()) {
                resumeDelayedFragmentAnimation();
            }
        }
        if (flagSecure != null) {
            flagSecure.invalidate();
        }
        updateTtlData();
    }

    public TLRPC.User getCurrentUser() {
        return userInfo == null ? null : userInfo.user;
    }

    public void setChatInfo(TLRPC.ChatFull value) {
        if (chatInfo instanceof TLRPC.TL_channelFull && value.participants == null) {
            value.participants = chatInfo.participants;
        }
        long previousMigrationId = chatInfo != null ? chatInfo.migrated_from_chat_id : 0;
        this.chatInfo = value;
        if (value != null && value.migrated_from_chat_id != 0 && value.migrated_from_chat_id != previousMigrationId) {
            getMediaDataController().getMediaCounts(-chatInfo.migrated_from_chat_id, topicId, classGuid);
        }
        if (sharedMediaLayout != null) {
            sharedMediaLayout.setChatInfo(value);
        }
        TLRPC.Chat newChat = getMessagesController().getChat(chatId);
        if (newChat != null) {
            chat = newChat;
            updateActionBarMenuItems(true);
        }
        if (flagSecure != null) {
            flagSecure.invalidate();
        }
        updateTtlData();
        // WIP more
    }

    public TLRPC.Chat getCurrentChat() {
        return chat;
    }

    @SuppressWarnings("deprecation")
    public UndoView getUndoView() {
        return rootLayout != null ? rootLayout.undoView : null;
    }

    public long getDialogId() {
        if (dialogId != 0) return dialogId;
        if (userId != 0) return userId;
        return -chatId;
    }

    public long getTopicId() {
        return topicId;
    }

    public boolean isChat() {
        return chatId != 0;
    }

    public boolean isSettings() {
        return imageUpdater != null && !isMyProfile;
    }

    public boolean isMyProfile() {
        return isMyProfile;
    }

    public boolean isSaved() {
        return isSaved;
    }

    private void updateProfileData(boolean reload) {
        if (getParentActivity() == null) return;
        TLRPC.EmojiStatus peerColorEmojiStatus = null;
        int peerColorFallbackId = 0;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            peerColorEmojiStatus = user.emoji_status;
            peerColorFallbackId = UserObject.getProfileColorId(user);
        } else if (chatId != 0) {
            TLRPC.Chat newChat = getMessagesController().getChat(chatId);
            chat = newChat != null ? newChat : chat;
            if (chat == null) return;
            peerColorEmojiStatus = chat.emoji_status;
            peerColorFallbackId = ChatObject.getProfileColorId(chat);
        }
        if (flagSecure != null) {
            flagSecure.invalidate();
        }

        final MessagesController.PeerColor wasPeerColor = peerColor;
        peerColor = MessagesController.PeerColor.fromCollectible(peerColorEmojiStatus);
        if (peerColor == null) {
            final MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
            peerColor = peerColors == null ? null : peerColors.getColor(peerColorFallbackId);
        }
        if (wasPeerColor != peerColor) {
            // WIP: updatedPeerColor() method.
            if (rootLayout != null) {
                rootLayout.updateColors(peerColor, mediaHeaderAnimationProgress);
            }
            if (sharedMediaLayout != null && sharedMediaLayout.scrollSlidingTextTabStrip != null) {
                sharedMediaLayout.scrollSlidingTextTabStrip.updateColors();
            }
            if (sharedMediaLayout != null && sharedMediaLayout.giftsContainer != null) {
                sharedMediaLayout.giftsContainer.updateColors();
            }
        }
    }

    private void updateTtlData() {
        if (rootLayout == null) return;
        int ttl = userInfo != null ? userInfo.ttl_period : chatInfo != null ? chatInfo.ttl_period : 0;
        boolean visible = chatEncrypted == null && ttl > 0 && (chat == null || ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES));
        rootLayout.updateTtlIndicator(visible, isOpen);
        rootLayout.updateTtlPopup(ttl);
    }

    // LIFECYCLE

    @Override
    public void setParentLayout(INavigationLayout layout) {
        super.setParentLayout(layout);
        if (flagSecure != null) {
            flagSecure.detach();
            flagSecure = null;
        }
        if (layout != null && layout.getParentActivity() != null) {
            flagSecure = new FlagSecureReason(layout.getParentActivity().getWindow(),
                    () -> chatEncrypted != null || getMessagesController().isChatNoForwards(chat));
        }
    }

    @Override
    public boolean onFragmentCreate() {
        userId = arguments.getLong("user_id", 0);
        chatId = arguments.getLong("chat_id", 0);
        topicId = arguments.getLong("topic_id", 0);
        isSaved = arguments.getBoolean("saved", false);
        isMyProfile = arguments.getBoolean("my_profile", false);
        // WIP: Expand photo and other vars

        if (userId != 0) {
            dialogId = arguments.getLong("dialog_id", 0);
            if (dialogId != 0) {
                chatEncrypted = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            }
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return false;
            subscribeToNotifications();
            if (user.bot) {
                getMediaDataController().loadBotInfo(user.id, user.id, true, classGuid);
            }
            if (userInfo == null) {
                userInfo = getMessagesController().getUserFull(userId);
            }
            getMessagesController().loadFullUser(user, classGuid, true);
            if (UserObject.isUserSelf(user)) {
                imageUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
                imageUpdater.setOpenWithFrontfaceCamera(true);
                imageUpdater.parentFragment = this;
                imageUpdater.setDelegate(this);
                getMediaDataController().checkFeaturedStickers();
                getMessagesController().loadSuggestedFilters();
                getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, classGuid);
                if (!isMyProfile) getMessagesController().getContentSettings(null);
                getConnectionsManager().sendRequest(new TL_account.getPassword(), (response, error) -> {
                    if (response instanceof TL_account.TL_password) userPassword = (TL_account.TL_password) response;
                });
            }

            // WIP: actionBarAnimationColorFrom = arguments.getInt("actionBarColor", 0);

        } else if (chatId != 0) {
            chat = getMessagesController().getChat(chatId);
            if (chat == null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    chat = getMessagesStorage().getChat(chatId);
                    countDownLatch.countDown();
                });
                try {
                    countDownLatch.await();
                    getMessagesController().putChat(chat, true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (chat == null) return false;
            subscribeToNotifications();
            if (chatInfo == null) {
                chatInfo = getMessagesController().getChatFull(chatId);
            }
            if (ChatObject.isChannel(chat)) {
                getMessagesController().loadFullChat(chatId, classGuid, true);
            } else if (chatInfo == null) {
                chatInfo = getMessagesStorage().loadChatInfo(chatId, false, null, false, false);
            }
        } else {
            return false;
        }
        
        if (flagSecure != null) {
            flagSecure.invalidate();
        }
        if (sharedMediaPreloader == null) {
            sharedMediaPreloader = new SharedMediaLayout.SharedMediaPreloader(this);
        }
        sharedMediaPreloader.addDelegate(this);

        if (arguments.containsKey("preload_messages")) {
            getMessagesController().ensureMessagesLoaded(userId, 0, null);
        }

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override public int getTopOffset(int tag) { return AndroidUtilities.statusBarHeight; }
        });

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        Bulletin.removeDelegate(this);
        
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onDestroy();
        }
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.onDestroy(this);
        }
        if (sharedMediaPreloader != null) {
            sharedMediaPreloader.removeDelegate(this);
        }
        if (imageUpdater != null) {
            imageUpdater.clear();
        }
        unsubscribeFromNotifications();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onResume();
        }
        if (!parentLayout.isInPreviewMode() && rootLayout != null) {
            rootLayout.hideBlurredView();
        }
        if (flagSecure != null) {
            flagSecure.attach();
        }
        if (imageUpdater != null) {
            imageUpdater.onResume();
            setParentActivityTitle(LocaleController.getString(R.string.Settings));
        }
        updateProfileData(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (rootLayout != null) {
            rootLayout.hideUndoView(0);
        }
        if (imageUpdater != null) {
            imageUpdater.onPause();
        }
        if (flagSecure != null) {
            flagSecure.detach();
        }
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onPause();
        }
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        if (rootLayout != null) rootLayout.hideUndoView(0);
    }

    @Override
    public boolean canBeginSlide() {
        if (!sharedMediaLayout.isSwipeBackEnabled()) return false;
        return super.canBeginSlide();
    }

    @Override
    public void dismissCurrentDialog() {
        if (imageUpdater != null && imageUpdater.dismissCurrentDialog(visibleDialog)) return;
        super.dismissCurrentDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return (imageUpdater == null || imageUpdater.dismissDialogOnPause(dialog)) && super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (imageUpdater != null) {
            imageUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (imageUpdater != null) {
            imageUpdater.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        }
        if (requestCode == 101 || requestCode == 102 || requestCode == 103) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (grantResults.length == 0 || !allGranted) {
                VoIPHelper.permissionDenied(getParentActivity(), null, requestCode);
            } else if (requestCode == 101 || requestCode == 102) {
                final TLRPC.User user = getMessagesController().getUser(userId);
                if (user == null) return;
                VoIPHelper.startCall(user, requestCode == 102, userInfo != null && userInfo.video_calls_available, getParentActivity(), userInfo, getAccountInstance());
            } else {
                if (chat == null) return;
                ChatObject.Call call = getMessagesController().getGroupCall(chatId, false);
                VoIPHelper.startCall(chat, null, null, call == null, getParentActivity(), ProfileActivityReplacement.this, getAccountInstance());
            }
        }
    }

    @Override
    public void saveSelfArgs(Bundle args) {
        if (imageUpdater != null && imageUpdater.currentPicturePath != null) {
            args.putString("path", imageUpdater.currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (imageUpdater != null) {
            imageUpdater.currentPicturePath = args.getString("path");
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        this.isOpen = isOpen; // WIP: pass to isFragmentOpened()
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        super.onTransitionAnimationProgress(isOpen, progress);
        if (rootLayout != null) rootLayout.animateBlurredView(isOpen, progress);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) {
            if (rootLayout != null) rootLayout.hideBlurredView();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onConfigurationChanged(newConfig);
        }
    }

    // MISC

    @Override
    public void mediaCountUpdated() {
        if (sharedMediaLayout != null && sharedMediaPreloader != null) {
            sharedMediaLayout.setNewMediaCounts(sharedMediaPreloader.getLastMediaCount());
        }
        // WIP updateListAnimated(false);
        // WIP updateSelectedMediaTabText();
        if (userInfo != null) {
            resumeDelayedFragmentAnimation();
        }
    }

    public void updateGifts() {
        // WIP giftsView.update()
    }

    public void scrollToSharedMedia(boolean animated) {
        // WIP
    }

    public void prepareBlurBitmap() {
        if (rootLayout != null) rootLayout.prepareBlurredView();
    }

    // VIEW HIERARCHY


    @Override
    public ActionBar createActionBar(Context context) {
        checkThemeResourceProvider();
        final ActionBar actionBar = new ActionBar(context, getResourceProvider()) {
            // WIP: Overrides
        };
        actionBar.setForceSkipTouches(true);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21 && !AndroidUtilities.isTablet() && !inBubbleMode);
        final ImageView backButton = actionBar.getBackButton();
        backButton.setOnLongClickListener(e -> {
            ActionBarPopupWindow menu = BackButtonMenu.show(this, backButton, getDialogId(), getTopicId(), getResourceProvider());
            if (menu != null) {
                menu.setOnDismissListener(() -> {
                    if (rootLayout != null) rootLayout.dimBehindView(0, null);
                });
                if (rootLayout != null) {
                    rootLayout.dimBehindView(0.3F, backButton);
                    rootLayout.hideUndoView(1);
                }
                return true;
            } else {
                return false;
            }
        });
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (getParentActivity() == null) return;
                if (id == -1) {
                    finishFragment();
                } else if (id == RootLayout.EDIT_INFO_ID) {
                    presentFragment(new UserInfoActivity());
                } else if (id == RootLayout.ADD_PHOTO_ID) {
                    uploadUserImage(null, null);
                } else if (id == RootLayout.EDIT_ID) {
                    if (isMyProfile) {
                        presentFragment(new UserInfoActivity());
                    } else if (topicId != 0) {
                        Bundle args = new Bundle();
                        args.putLong("chat_id", chatId);
                        TopicCreateFragment fragment = TopicCreateFragment.create(chatId, topicId);
                        presentFragment(fragment);
                    } else {
                        Bundle args = new Bundle();
                        if (chatId != 0) {
                            args.putLong("chat_id", chatId);
                        } else if (userInfo != null && userInfo.user.bot) {
                            args.putLong("user_id", userId);
                        }
                        ChatEditActivity fragment = new ChatEditActivity(args);
                        if (chatInfo != null) fragment.setInfo(chatInfo);
                        else fragment.setInfo(userInfo);
                        presentFragment(fragment);
                    }
                } else {
                    super.onItemClick(id);
                }
            }
        });
        return actionBar;
    }

    private void updateActionBarMenuItems(boolean animated) {
        if (rootLayout == null) return;
        ActionBarMenuItem mainMenuItem = rootLayout.mainMenuItem;
        mainMenuItem.removeAllSubItems();
        boolean editMenuItemVisible = false;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            if (UserObject.isUserSelf(user)) {
                editMenuItemVisible = isMyProfile;
                mainMenuItem.addSubItem(RootLayout.EDIT_INFO_ID, R.drawable.msg_edit, LocaleController.getString(R.string.EditInfo));
                if (imageUpdater != null) {
                    mainMenuItem.addSubItem(RootLayout.ADD_PHOTO_ID, R.drawable.msg_addphoto, LocaleController.getString(R.string.AddPhoto));
                }
            } else {
                editMenuItemVisible = user.bot && user.bot_can_edit;
                if (user.bot || getContactsController().contactsDict.get(userId) == null) {
                    if (MessagesController.isSupportUser(user)) {
                        // WIP
                    } else if (getDialogId() != UserObject.VERIFY) {
                        if (chatEncrypted == null) updateActionBarAutoDeleteMenuItem(rootLayout);
                    }
                } else {
                    if (chatEncrypted == null) updateActionBarAutoDeleteMenuItem(rootLayout);
                }
            }
        } else if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat == null) return;
            if (topicId == 0 && ChatObject.canChangeChatInfo(chat)) {
                updateActionBarAutoDeleteMenuItem(rootLayout);
            }
            if (ChatObject.isChannel(chat)) {
                if (topicId != 0) {
                    editMenuItemVisible = ChatObject.canManageTopic(currentAccount, chat, topicId);
                } else {
                    editMenuItemVisible = ChatObject.hasAdminRights(chat) || chat.megagroup && ChatObject.canChangeChatInfo(chat);
                }
            } else {
                editMenuItemVisible = ChatObject.canChangeChatInfo(chat);
            }
        }
        updateActionBarEditMenuItem(rootLayout, editMenuItemVisible, animated);
        // WIP
    }

    private void updateActionBarEditMenuItem(RootLayout rootLayout, boolean visible, boolean animated) {
        boolean actuallyVisible = visible && !mediaHeaderVisible;
        ActionBarMenuItem editMenuItem = rootLayout.editMenuItem;
        if (actuallyVisible && editMenuItem.getVisibility() != View.VISIBLE) {
            editMenuItem.setVisibility(View.VISIBLE);
            if (animated) {
                editMenuItem.setAlpha(0);
                editMenuItem.animate().alpha(1f).setDuration(150).start();
            }
        } else if (!actuallyVisible && editMenuItem.getVisibility() != View.GONE) {
            editMenuItem.setVisibility(View.GONE);
        }
    }

    private void updateActionBarAutoDeleteMenuItem(RootLayout rootLayout) {
        rootLayout.appendTtlMenuItem(dialogId > 0 || userId > 0, new AutoDeletePopupWrapper.Callback() {
            @Override
            public void dismiss() {
                rootLayout.mainMenuItem.toggleSubMenu();
            }
            @Override
            public void setAutoDeleteHistory(int time, int action) {
                long did = getDialogId();
                getMessagesController().setDialogHistoryTTL(did, time);
                rootLayout.undoView.showWithAction(did, action, getMessagesController().getUser(did), time, null, null);
            }
            @Override
            public void showGlobalAutoDeleteScreen() {
                presentFragment(new AutoDeleteMessagesActivity());
                dismiss();
            }
        });
        updateTtlData();
    }

    private boolean uploadUserImage(Runnable onDismiss, Runnable onDelete) {
        if (imageUpdater == null) return false;
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        if (user == null) user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (user == null) return false;
        imageUpdater.openMenu(user.photo != null && user.photo.photo_big != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty), () -> {
            MessagesController.getInstance(currentAccount).deleteUserPhoto(null);
            if (onDelete != null) onDelete.run();
        }, dialog -> {
            if (onDismiss != null) onDismiss.run();
        }, 0);
        return true;
    }

    @Override
    public View createView(Context context) {
        // Resource management
        Theme.createProfileResources(context);
        Theme.createChatResources(context, false);
        checkThemeResourceProvider();

        // Preparation
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onDestroy();
        }

        // Root view
        rootLayout = new RootLayout(context, resourceProvider, actionBar);

        rootLayout.updateColors(peerColor, 0F);
        updateActionBarMenuItems(false);


        // Decorations
        rootLayout.blurredView.setOnClickListener(e -> { finishPreviewFragment(); });
        rootLayout.addDecorationViews();
        rootLayout.setBackgroundColor(Color.BLACK);

        updateProfileData(true);
        updateTtlData();

        return rootLayout;
    }

    // THEME

    private void checkThemeResourceProvider() {
        BaseFragment lastFragment = parentLayout.getLastFragment();
        if (lastFragment instanceof ChatActivity && ((ChatActivity) lastFragment).themeDelegate != null && ((ChatActivity) lastFragment).themeDelegate.getCurrentTheme() != null) {
            setResourceProvider(lastFragment.getResourceProvider());
        }
    }

    @Override
    public Drawable getThemedDrawable(String drawableKey) {
        Drawable drawable = getResourceProvider() != null ? getResourceProvider().getDrawable(drawableKey) : null;
        return drawable != null ? drawable : super.getThemedDrawable(drawableKey);
    }

    /** @noinspection deprecation*/
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        if (getResourceProvider() != null) return null;
        
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        if (sharedMediaLayout != null) {
            arrayList.addAll(sharedMediaLayout.getThemeDescriptions());
        }

        ThemeDescription.ThemeDescriptionDelegate themeDelegate = () -> {
            // WIP
            if (rootLayout != null) rootLayout.updateColors(peerColor, mediaHeaderAnimationProgress);
        };
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_actionBarSelectorBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_chat_lockIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_subtitleInProfileBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundActionBarBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_profile_title));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_profile_status));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_subtitleInProfileBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundRed));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundOrange));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundViolet));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundGreen));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundCyan));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, themeDelegate, Theme.key_avatar_backgroundPink));

        UndoView undoView = getUndoView();
        arrayList.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        arrayList.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
        arrayList.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
        arrayList.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        arrayList.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        arrayList.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        arrayList.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));

        return arrayList;
    }

    // AVATAR UPLOAD

    @Override
    public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {
        onUploadProgressChanged(0);
    }

    @Override
    public void onUploadProgressChanged(float progress) {
        // WIP Avatar
    }

    @Override
    public void didUploadPhoto(TLRPC.InputFile photo, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
        // WIP Avatar
    }

    // NOTIFICATIONS

    private void subscribeToNotifications() {
        if (userId != 0) {
            getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.newSuggestionsAvailable);
            getNotificationCenter().addObserver(this, NotificationCenter.encryptedChatCreated);
            getNotificationCenter().addObserver(this, NotificationCenter.encryptedChatUpdated);
            getNotificationCenter().addObserver(this, NotificationCenter.blockedUsersDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.botInfoDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.reloadInterface);
        } else if (chatId != 0) {
            getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.chatOnlineCountDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.groupCallUpdated);
            getNotificationCenter().addObserver(this, NotificationCenter.channelRightsUpdated);
            getNotificationCenter().addObserver(this, NotificationCenter.chatWasBoostedByUser);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.uploadStoryEnd);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
        getNotificationCenter().addObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().addObserver(this, NotificationCenter.topicsDidLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.updateSearchSettings);
        getNotificationCenter().addObserver(this, NotificationCenter.reloadDialogPhotos);
        getNotificationCenter().addObserver(this, NotificationCenter.storiesUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.storiesReadUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.userIsPremiumBlockedUpadted);
        getNotificationCenter().addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.starBalanceUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.botStarsUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.botStarsTransactionsLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogDeleted);
        getNotificationCenter().addObserver(this, NotificationCenter.channelRecommendationsLoaded);
        getNotificationCenter().addObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    private void unsubscribeFromNotifications() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
        getNotificationCenter().removeObserver(this, NotificationCenter.topicsDidLoaded);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateSearchSettings);
        getNotificationCenter().removeObserver(this, NotificationCenter.reloadDialogPhotos);
        getNotificationCenter().removeObserver(this, NotificationCenter.storiesUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.storiesReadUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.userIsPremiumBlockedUpadted);
        getNotificationCenter().removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.starBalanceUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.botStarsUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.botStarsTransactionsLoaded);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogDeleted);
        getNotificationCenter().removeObserver(this, NotificationCenter.channelRecommendationsLoaded);
        getNotificationCenter().removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (userId != 0) {
            getNotificationCenter().removeObserver(this, NotificationCenter.newSuggestionsAvailable);
            getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.encryptedChatCreated);
            getNotificationCenter().removeObserver(this, NotificationCenter.encryptedChatUpdated);
            getNotificationCenter().removeObserver(this, NotificationCenter.blockedUsersDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.botInfoDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.reloadInterface);
            getMessagesController().cancelLoadFullUser(userId);
        } else if (chatId != 0) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.uploadStoryEnd);
            getNotificationCenter().removeObserver(this, NotificationCenter.chatWasBoostedByUser);
            getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.chatOnlineCountDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.groupCallUpdated);
            getNotificationCenter().removeObserver(this, NotificationCenter.channelRightsUpdated);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        // WIP
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (userId != 0) {
                boolean infoChanged = (mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0;
                if (infoChanged) updateProfileData(true);
            } else if (chatId != 0) {
                boolean infoChanged = ((mask & MessagesController.UPDATE_MASK_CHAT) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0);
                if (infoChanged) updateProfileData(true);
            }
        } else if (id == NotificationCenter.chatOnlineCountDidLoad) {
            Long chatId = (Long) args[0];
            if (chatInfo == null || chat == null || chat.id != chatId) return;
            chatInfo.online_count = (Integer) args[1];
            updateProfileData(false);
        } else if (id == NotificationCenter.topicsDidLoaded) {
            if (topicId != 0) updateProfileData(false);
        } else if (id == NotificationCenter.reloadDialogPhotos) {
            updateProfileData(false);
        } else if (id == NotificationCenter.contactsDidLoad || id == NotificationCenter.channelRightsUpdated) {
            updateActionBarMenuItems(true);
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            final TLRPC.ChatFull full = (TLRPC.ChatFull) args[0];
            if (full.id != chatId) return;
            setChatInfo(full);
            updateTtlData();
        } else if (id == NotificationCenter.userInfoDidLoad) {
            final long uid = (Long) args[0];
            if (uid != userId) return;
            final TLRPC.UserFull full = (TLRPC.UserFull) args[1];
            setUserInfo(full);
            updateTtlData();
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack(true);
        }
    }

    // Views

    private static class RootLayout extends FrameLayout {
        private final static int MAIN_ID = 10;
        private final static int EDIT_ID = 41;
        private final static int EDIT_INFO_ID = 30;
        private final static int ADD_PHOTO_ID = 36;

        /** @noinspection deprecation*/
        private final UndoView undoView;
        private final View blurredView;

        private AnimatorSet scrimAnimatorSet;
        private View scrimView;
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ActionBar actionBar;
        private final Paint actionBarBackButtonBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ActionBarMenuItem mainMenuItem;
        private final ActionBarMenuItem editMenuItem;

        private final ImageView ttlIndicator;
        private AutoDeletePopupWrapper ttlPopupWrapper;
        private TimerDrawable ttlTimerDrawable;

        private final Theme.ResourcesProvider resourceProvider;

        RootLayout(Context context, Theme.ResourcesProvider resourceProvider, ActionBar actionBar) {
            super(context);
            setWillNotDraw(false);
            this.resourceProvider = resourceProvider;

            // Action bar
            this.actionBar = actionBar;
            this.actionBarBackButtonBackgroundPaint.setColor(getColor(Theme.key_listSelector));

            editMenuItem = actionBar.createMenu().addItem(EDIT_ID, R.drawable.group_edit_profile, resourceProvider);
            editMenuItem.setContentDescription(LocaleController.getString(R.string.Edit));
            mainMenuItem = actionBar.createMenu().addItem(MAIN_ID, R.drawable.ic_ab_other, resourceProvider);
            mainMenuItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
            ttlIndicator = new ImageView(context);
            ttlIndicator.setImageResource(R.drawable.msg_mini_autodelete_timer);
            mainMenuItem.addView(ttlIndicator, LayoutHelper.createFrame(12, 12, Gravity.CENTER_VERTICAL | Gravity.LEFT, 8, 2, 0, 0));
            updateTtlIndicator(false, false);

            //noinspection deprecation
            undoView = new UndoView(context, null, false, resourceProvider);
            blurredView = new View(context) {
                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    RootLayout.this.invalidate(); // Is this really needed?
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int color = getColor(Theme.key_windowBackgroundWhite);
                blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(color, 100)));
            }
            blurredView.setFocusable(false);
            blurredView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            blurredView.setVisibility(View.GONE);
            blurredView.setFitsSystemWindows(true);
            scrimPaint.setAlpha(0);
        }

        void addDecorationViews() {
            if (actionBar.getParent() != null) {
                ((ViewGroup) actionBar.getParent()).removeView(actionBar);
            }
            addView(actionBar);
            addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
            addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        void prepareBlurredView() {
            int w = (int) (getMeasuredWidth() / 6.0f);
            int h = (int) (getMeasuredHeight() / 6.0f);
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
            draw(canvas);
            Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
            blurredView.setBackground(new BitmapDrawable(bitmap));
            blurredView.setAlpha(0.0f);
            blurredView.setVisibility(View.VISIBLE);
        }

        void animateBlurredView(boolean isOpen, float progress) {
            if (blurredView.getVisibility() == View.VISIBLE) {
                blurredView.setAlpha(isOpen ? 1.0f - progress : progress);
            }
        }

        void hideBlurredView() {
            if (blurredView.getVisibility() == View.VISIBLE) {
                blurredView.setVisibility(View.GONE);
                blurredView.setBackground(null);
            }
        }

        void hideUndoView(int animatedFlag) {
            undoView.hide(true, animatedFlag);
        }

        void dimBehindView(float value, View view) {
            if (view != null) scrimView = view;
            boolean enable = value > 0;
            invalidate();
            if (scrimAnimatorSet != null) scrimAnimatorSet.cancel();
            scrimAnimatorSet = new AnimatorSet();
            final float startValue = enable ? 0 : scrimPaint.getAlpha() / 255F;
            final float endValue = enable ? value : 0F;
            ValueAnimator scrimPaintAlphaAnimator = ValueAnimator.ofFloat(startValue, endValue);
            scrimPaintAlphaAnimator.addUpdateListener(a -> {
                scrimPaint.setAlpha((int) (255 * (float) a.getAnimatedValue()));
                invalidate();
            });
            scrimAnimatorSet.playTogether(scrimPaintAlphaAnimator);
            scrimAnimatorSet.setDuration(enable ? 150 : 220);
            if (!enable) {
                scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        scrimView = null;
                        invalidate();
                    }
                });
            }
            scrimAnimatorSet.start();
        }

        int getColor(int key) {
            return Theme.getColor(key, resourceProvider);
        }

        void updateColors(MessagesController.PeerColor peerColor, float actionModeProgress) {
            int rawBackground = peerColor != null ? Theme.ACTION_BAR_WHITE_SELECTOR_COLOR : getColor(Theme.key_avatar_actionBarSelectorBlue);
            int rawForeground = peerColor != null ? Color.WHITE : getColor(Theme.key_actionBarDefaultIcon);
            int foreground = ColorUtils.blendARGB(rawForeground, getColor(Theme.key_actionBarActionModeDefaultIcon), actionModeProgress);
            actionBar.setItemsBackgroundColor(ColorUtils.blendARGB(rawBackground, getColor(Theme.key_actionBarActionModeDefaultSelector), actionModeProgress), false);
            actionBar.setItemsColor(foreground, false);
            mainMenuItem.setIconColor(rawForeground);
            ttlIndicator.setColorFilter(new PorterDuffColorFilter(foreground, PorterDuff.Mode.MULTIPLY));
            if (ttlPopupWrapper != null && ttlPopupWrapper.textView != null) {
                ttlPopupWrapper.textView.invalidate();
            }
        }

        void updateTtlIndicator(boolean visible, boolean animated) {
            AndroidUtilities.updateViewVisibilityAnimated(ttlIndicator, visible, 0.8f, animated);
        }

        void updateTtlPopup(int ttl) {
            if (ttlTimerDrawable != null) ttlTimerDrawable.setTime(ttl);
            if (ttlPopupWrapper != null) ttlPopupWrapper.updateItems(ttl);
        }

        void appendTtlMenuItem(boolean allowExtendedLink, AutoDeletePopupWrapper.Callback callback) {
            ttlPopupWrapper = new AutoDeletePopupWrapper(getContext(), mainMenuItem.getPopupLayout().getSwipeBack(), callback, false, 0, resourceProvider);
            if (allowExtendedLink) {
                ttlPopupWrapper.allowExtendedHint(getColor(Theme.key_windowBackgroundWhiteBlueText));
            }
            ttlTimerDrawable = TimerDrawable.getTtlIcon(0);
            mainMenuItem.addSwipeBackItem(0, ttlTimerDrawable, LocaleController.getString(R.string.AutoDeletePopupTitle), ttlPopupWrapper.windowLayout);
            mainMenuItem.addColoredGap();
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == blurredView) return true;
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (scrimPaint.getAlpha() > 0) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
            }
            if (scrimView != null) {
                int c = canvas.save();
                canvas.translate(scrimView.getLeft(), scrimView.getTop());
                if (scrimView == actionBar.getBackButton()) {
                    int r = Math.max(scrimView.getMeasuredWidth(), scrimView.getMeasuredHeight()) / 2;
                    int wasAlpha = actionBarBackButtonBackgroundPaint.getAlpha();
                    actionBarBackButtonBackgroundPaint.setAlpha((int) (wasAlpha * (scrimPaint.getAlpha() / 255f) / 0.3f));
                    canvas.drawCircle(r, r, r * 0.7f, actionBarBackButtonBackgroundPaint);
                    actionBarBackButtonBackgroundPaint.setAlpha(wasAlpha);
                }
                scrimView.draw(canvas);
                canvas.restoreToCount(c);
            }
            if (blurredView.getVisibility() == View.VISIBLE) {
                if (blurredView.getAlpha() != 1f && blurredView.getAlpha() != 0) {
                    canvas.saveLayerAlpha(blurredView.getLeft(), blurredView.getTop(), blurredView.getRight(), blurredView.getBottom(), (int) (255 * blurredView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                } else {
                    canvas.save();
                }
                canvas.translate(blurredView.getLeft(), blurredView.getTop());
                blurredView.draw(canvas);
                canvas.restore();
            }
        }
    }
}
