package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.ProfileChannelCell;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Profile.ProfileActivityMenus;
import org.telegram.ui.Profile.ProfileActivityRootLayout;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.bots.BotWebViewAttachedSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.telegram.messenger.ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.ui.Profile.ProfileActivityMenus.*;

public class ProfileActivityReplacement extends BaseFragment implements
        SharedMediaLayout.SharedMediaPreloaderDelegate,
        ImageUpdater.ImageUpdaterDelegate,
        NotificationCenter.NotificationCenterDelegate,
        DialogsActivity.DialogsActivityDelegate {

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
    private boolean isReportSpam;
    private boolean isUserBlocked;
    private boolean isCreatingEncryptedChat;
    private boolean isBotWithPrivacyPolicy;

    public SharedMediaLayout sharedMediaLayout;
    private ProfileActivityRootLayout rootLayout;
    private ProfileActivityMenus menuHandler;

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
        if (userInfo != null && userInfo.bot_info != null) {
            isBotWithPrivacyPolicy = userInfo.bot_info.privacy_policy_url != null || userInfo.bot_info.commands.stream().anyMatch((c) -> "privacy".equals(c.command));
        } else {
            isBotWithPrivacyPolicy = false;
        }
        if (menuHandler != null) {
            menuHandler.updateBotViewPrivacyItem(isBotWithPrivacyPolicy);
        }
        updateTtlData();
        updatePremiumData();
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
        if (chatInfo != null && chatInfo.migrated_from_chat_id != 0 && chatInfo.migrated_from_chat_id != previousMigrationId) {
            getMediaDataController().getMediaCounts(-chatInfo.migrated_from_chat_id, topicId, classGuid);
        }
        if (sharedMediaLayout != null) {
            sharedMediaLayout.setChatInfo(chatInfo);
        }
        TLRPC.Chat newChat = getMessagesController().getChat(chatId);
        if (newChat != null) {
            chat = newChat;
            updateMenuData(true);
        }
        if (flagSecure != null) {
            flagSecure.invalidate();
        }
        updateTtlData();
        if (menuHandler != null) {
            boolean canPurchase = !BuildVars.IS_BILLING_UNAVAILABLE && !getMessagesController().premiumPurchaseBlocked();
            boolean canSendGifts = chatInfo != null && chatInfo.stargifts_available;
            menuHandler.updateSendGiftsItem(canPurchase && canSendGifts);
        }
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
            if (menuHandler != null) {
                menuHandler.updateUsernameRelatedItems(UserObject.getPublicUsername(user) != null);
            }
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
            updateColors();
        }
        if (menuHandler != null) {
            menuHandler.updateQrItem(true, true);
        }
    }

    private void updateColors() {
        if (rootLayout != null) {
            rootLayout.updateColors(peerColor, mediaHeaderAnimationProgress);
        }
        if (menuHandler != null) {
            menuHandler.updateColors(peerColor, mediaHeaderAnimationProgress);
        }
        if (sharedMediaLayout != null && sharedMediaLayout.scrollSlidingTextTabStrip != null) {
            sharedMediaLayout.scrollSlidingTextTabStrip.updateColors();
        }
        if (sharedMediaLayout != null && sharedMediaLayout.giftsContainer != null) {
            sharedMediaLayout.giftsContainer.updateColors();
        }
    }

    private void updateMenuData(boolean animated) {
        if (menuHandler == null) return;
        menuHandler.clearMainMenu();
        boolean editMenuItemVisible = false;
        boolean appendLogout = false;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            if (UserObject.isUserSelf(user)) {
                editMenuItemVisible = isMyProfile;
                appendLogout = !isMyProfile;
                menuHandler.appendMainMenuSubItem(AB_EDIT_INFO_ID);
                if (imageUpdater != null) menuHandler.appendMainMenuSubItem(AB_ADD_PHOTO_ID);
                menuHandler.appendMainMenuSubItem(AB_EDIT_COLOR_ID);
                updatePremiumData(); // AB_EDIT_COLOR_ID has different icons
                if (isMyProfile) {
                    menuHandler.appendMainMenuSubItem(AB_PROFILE_COPY_LINK_ID);
                    menuHandler.appendMainMenuSubItem(AB_PROFILE_SET_USERNAME_ID);
                    menuHandler.updateUsernameRelatedItems(UserObject.getPublicUsername(user) != null);
                }
            } else {
                editMenuItemVisible = user.bot && user.bot_can_edit;
                if (user.bot || getContactsController().contactsDict.get(user.id) == null) {
                    if (MessagesController.isSupportUser(user)) {
                        menuHandler.appendBlockUnblockItem(false, isUserBlocked);
                        menuHandler.appendMainMenuSubItem(AB_SHORTCUT_ID);
                    } else if (getDialogId() != UserObject.VERIFY) {
                        if (chatEncrypted == null) appendMenuAutoDelete();
                        menuHandler.appendMainMenuSubItem(AB_SHORTCUT_ID);
                        if (!user.bot) {
                            menuHandler.appendMainMenuSubItem(AB_CONTACT_ADD_ID);
                        }
                        if (user.bot) {
                            menuHandler.appendMainMenuSubItem(AB_SHARE_ID);
                        }
                        if (!TextUtils.isEmpty(user.phone)) {
                            menuHandler.appendMainMenuSubItem(AB_CONTACT_SHARE_ID);
                        }
                        if (user.bot) {
                            menuHandler.appendMainMenuSubItem(AB_BOT_VIEW_PRIVACY_ID);
                            menuHandler.updateBotViewPrivacyItem(isBotWithPrivacyPolicy);
                            menuHandler.appendMainMenuSubItem(AB_REPORT_ID);
                        }
                        menuHandler.appendBlockUnblockItem(user.bot, isUserBlocked);
                    }
                } else {
                    if (chatEncrypted == null) appendMenuAutoDelete();
                    if (!TextUtils.isEmpty(user.phone)) menuHandler.appendMainMenuSubItem(AB_CONTACT_SHARE_ID);
                    menuHandler.appendBlockUnblockItem(false, isUserBlocked);
                    menuHandler.appendMainMenuSubItem(AB_CONTACT_EDIT_ID);
                    menuHandler.appendMainMenuSubItem(AB_CONTACT_DELETE_ID);
                }
                if (!UserObject.isDeleted(user) && !user.bot && chatEncrypted == null && !isUserBlocked && userId != 333000 && userId != 777000 && userId != 42777) {
                    if (!BuildVars.IS_BILLING_UNAVAILABLE && !user.self && !MessagesController.isSupportUser(user) && !getMessagesController().premiumPurchaseBlocked()) {
                        StarsController.getInstance(currentAccount).loadStarGifts();
                        menuHandler.appendSendGiftsItem(false);
                    }
                    menuHandler.appendMainMenuSubItem(AB_START_SECRET_CHAT_ID);
                    menuHandler.updateStartSecretChatItem(getMessagesController().isUserContactBlocked(userId));
                }
                if (!user.bot && getContactsController().contactsDict.get(userId) != null) {
                    menuHandler.appendMainMenuSubItem(AB_SHORTCUT_ID);
                }
            }
        } else if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat == null) return;
            if (topicId == 0 && ChatObject.canChangeChatInfo(chat)) {
                appendMenuAutoDelete();
            }
            if (ChatObject.isChannel(chat)) {
                if (topicId != 0) {
                    editMenuItemVisible = ChatObject.canManageTopic(currentAccount, chat, topicId);
                } else {
                    editMenuItemVisible = ChatObject.hasAdminRights(chat) || chat.megagroup && ChatObject.canChangeChatInfo(chat);
                }
                if (topicId == 0 && chatInfo != null && (chatInfo.can_view_stats || chatInfo.can_view_revenue || chatInfo.can_view_stars_revenue || getMessagesController().getStoriesController().canPostStories(getDialogId()))) {
                    menuHandler.appendMainMenuSubItem(AB_STATISTICS_ID);
                }
                if (chat.megagroup) {

                    if (chatInfo == null || !chatInfo.participants_hidden || ChatObject.hasAdminRights(chat)) {
                        menuHandler.appendMainMenuSubItem(AB_SEARCH_MEMBERS_ID);
                    }
                    if (!chat.creator && !chat.left && !chat.kicked && topicId == 0) {
                        menuHandler.appendLeaveGroupItem(true, true);
                    }
                    if (topicId != 0 && ChatObject.canDeleteTopic(currentAccount, chat, topicId)) {
                        menuHandler.appendMainMenuSubItem(AB_DELETE_TOPIC_ID);
                    }
                } else {
                    if (chat.creator || chat.admin_rights != null && chat.admin_rights.edit_stories) {
                        menuHandler.appendMainMenuSubItem(AB_CHANNEL_STORIES_ID);
                    }
                    if (ChatObject.isPublic(chat)) menuHandler.appendMainMenuSubItem(AB_SHARE_ID);
                    if (!BuildVars.IS_BILLING_UNAVAILABLE && !getMessagesController().premiumPurchaseBlocked()) {
                        StarsController.getInstance(currentAccount).loadStarGifts();
                        menuHandler.appendSendGiftsItem(true);
                        menuHandler.updateSendGiftsItem(chatInfo != null && chatInfo.stargifts_available);
                    }
                    if (chatInfo != null && chatInfo.linked_chat_id != 0) menuHandler.appendMainMenuSubItem(AB_VIEW_DISCUSSION_ID);
                    if (!chat.creator && !chat.left && !chat.kicked) menuHandler.appendLeaveGroupItem(false, true);
                }
            } else {
                editMenuItemVisible = ChatObject.canChangeChatInfo(chat);
                if (!ChatObject.isKickedFromChat(chat) && !ChatObject.isLeftFromChat(chat)) {
                    if (chatInfo == null || !chatInfo.participants_hidden || ChatObject.hasAdminRights(chat)) {
                        menuHandler.appendMainMenuSubItem(AB_SEARCH_MEMBERS_ID);
                    }
                }
                menuHandler.appendLeaveGroupItem(false, false);
            }
            if (topicId == 0) {
                menuHandler.appendMainMenuSubItem(AB_SHORTCUT_ID);
            }
        }
        menuHandler.updateEditItem(editMenuItemVisible && !mediaHeaderVisible, animated);
        if (appendLogout) {
            menuHandler.appendMainMenuSubItem(AB_LOGOUT_ID);
        }
    }

    private void appendMenuAutoDelete() {
        if (menuHandler == null) return;
        menuHandler.appendAutoDeleteItem(dialogId > 0 || userId > 0, new AutoDeletePopupWrapper.Callback() {
            @Override public void dismiss() {}
            @Override
            public void setAutoDeleteHistory(int time, int action) {
                long did = getDialogId();
                //noinspection deprecation
                UndoView undo = getUndoView();
                getMessagesController().setDialogHistoryTTL(did, time);
                if (undo != null) undo.showWithAction(did, action, getMessagesController().getUser(did), time, null, null);
            }
            @Override
            public void showGlobalAutoDeleteScreen() {
                presentFragment(new AutoDeleteMessagesActivity());
            }
        });
        updateTtlData();
    }

    private void updatePremiumData() {
        if (menuHandler != null) {
            menuHandler.updateEditColorItem(getUserConfig().isPremium());
            menuHandler.updateStartSecretChatItem(getMessagesController().isUserContactBlocked(userId));
        }
    }

    private void updateTtlData() {
        if (menuHandler == null) return;
        int ttl = userInfo != null ? userInfo.ttl_period : chatInfo != null ? chatInfo.ttl_period : 0;
        boolean visible = chatEncrypted == null && ttl > 0 && (chat == null || ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_DELETE_MESSAGES));
        menuHandler.updateTtlIndicator(visible, isOpen);
        menuHandler.updateTtlPopup(ttl);
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
        isReportSpam = arguments.getBoolean("reportSpam", false);
        // WIP: Expand photo and other vars

        if (userId != 0) {
            dialogId = arguments.getLong("dialog_id", 0);
            if (dialogId != 0) {
                chatEncrypted = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            }
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return false;
            subscribeToNotifications();

            isUserBlocked = getMessagesController().blockePeers.indexOfKey(userId) >= 0;
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
    public boolean didSelectDialogs(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, TopicsFragment topicsFragment) {
        long did = dids.get(0).dialogId;
        Bundle args = new Bundle();
        args.putBoolean("scrollToTopOnResume", true);
        if (DialogObject.isEncryptedDialog(did)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
        } else if (DialogObject.isUserDialog(did)) {
            args.putLong("user_id", did);
        } else if (DialogObject.isChatDialog(did)) {
            args.putLong("chat_id", -did);
        }
        if (!getMessagesController().checkCanOpenChat(args, fragment)) {
            return false;
        }

        getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
        presentFragment(new ChatActivity(args), true);
        removeSelfFromStack();
        TLRPC.User user = getMessagesController().getUser(userId);
        getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(user, did, null, null, null, null, notify, scheduleDate));
        if (!TextUtils.isEmpty(message)) {
            AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
            SendMessagesHelper.prepareSendingText(accountInstance, message.toString(), did, notify, scheduleDate, 0);
        }
        return true;
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
                handleActionBarMenuClick(id);
            }
        });
        return actionBar;
    }

    public boolean uploadUserImage(Runnable onDismiss, Runnable onDelete) {
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
        boolean qr = userId == getUserConfig().clientUserId && !isMyProfile;
        rootLayout = new ProfileActivityRootLayout(context, resourceProvider, actionBar);
        menuHandler = new ProfileActivityMenus(context, resourceProvider, actionBar, qr);
        if (qr && ContactsController.getInstance(currentAccount).getPrivacyRules(PRIVACY_RULES_TYPE_ADDED_BY_PHONE) == null) {
            ContactsController.getInstance(currentAccount).loadPrivacySettings();
        }

        rootLayout.updateColors(peerColor, 0F);
        menuHandler.updateColors(peerColor, 0F);

        // Decorations
        rootLayout.blurredView.setOnClickListener(e -> { finishPreviewFragment(); });
        rootLayout.addDecorationViews();
        rootLayout.setBackgroundColor(Color.BLACK);

        updateProfileData(true);
        updateMenuData(false);

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

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        if (getResourceProvider() != null) return null;
        
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        if (sharedMediaLayout != null) {
            arrayList.addAll(sharedMediaLayout.getThemeDescriptions());
        }

        ThemeDescription.ThemeDescriptionDelegate themeDelegate = () -> {
            // WIP
            updateColors();
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

        if (rootLayout != null) {
            rootLayout.getThemeDescriptions(arrayList);
        }
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
            updateMenuData(true);
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            final TLRPC.ChatFull full = (TLRPC.ChatFull) args[0];
            if (full.id != chatId) return;
            setChatInfo(full);
        } else if (id == NotificationCenter.groupCallUpdated) {
            Long chatId = (Long) args[0];
            if (chat != null && chatId == chat.id && ChatObject.canManageCalls(chat)) {
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chatId);
                setChatInfo(chatFull);
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            final long uid = (Long) args[0];
            if (uid != userId) return;
            final TLRPC.UserFull full = (TLRPC.UserFull) args[1];
            setUserInfo(full);
            updateTtlData();
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack(true);
        } else if (id == NotificationCenter.privacyRulesUpdated) {
            if (menuHandler != null) menuHandler.updateQrItem(true, true);
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            updatePremiumData();
        } else if (id == NotificationCenter.userIsPremiumBlockedUpadted) {
            updatePremiumData();
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            boolean oldValue = isUserBlocked;
            isUserBlocked = getMessagesController().blockePeers.indexOfKey(userId) >= 0;
            if (oldValue != isUserBlocked) {
                updateMenuData(true);
                // WIP: updateListAnimated(false);
            }
        } else if (id == NotificationCenter.encryptedChatCreated) {
            if (!isCreatingEncryptedChat) return;
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                TLRPC.EncryptedChat encryptedChat = (TLRPC.EncryptedChat) args[0];
                Bundle args2 = new Bundle();
                args2.putInt("enc_id", encryptedChat.id);
                presentFragment(new ChatActivity(args2), true);
            });
        }
    }

    // CLICKS

    private void handleActionBarMenuClick(int id) {
        if (getParentActivity() == null) return;
        if (id == -1) {
            finishFragment();
        } else if (id == AB_EDIT_INFO_ID) {
            presentFragment(new UserInfoActivity());
        } else if (id == AB_ADD_PHOTO_ID) {
            uploadUserImage(null, null);
        } else if (id == AB_EDIT_ID) {
            if (isMyProfile()) {
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
        } else if (id == AB_QR_ID) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putLong("user_id", userId);
            presentFragment(new QrActivity(args));
        } else if (id == AB_LOGOUT_ID) {
            presentFragment(new LogoutActivity());
        } else if (id == AB_EDIT_COLOR_ID) {
            if (!getUserConfig().isPremium()) {
                showDialog(new PremiumFeatureBottomSheet(ProfileActivityReplacement.this, PremiumPreviewFragment.PREMIUM_FEATURE_NAME_COLOR, true));
            } else {
                presentFragment(new PeerColorActivity(0).startOnProfile().setOnApplied(ProfileActivityReplacement.this));
            }
        } else if (id == AB_CONTACT_DELETE_ID) {
            final TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(LocaleController.getString(R.string.DeleteContact));
            builder.setMessage(LocaleController.getString(R.string.AreYouSureDeleteContact));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialogInterface, i) -> {
                ArrayList<TLRPC.User> list = new ArrayList<>(); list.add(user);
                getContactsController().deleteContact(list, true);
                user.contact = false;
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        } else if (id == AB_CONTACT_EDIT_ID) {
            Bundle args = new Bundle();
            args.putLong("user_id", userId);
            presentFragment(new ContactAddActivity(args, getResourceProvider()));
        } else if (id == AB_CONTACT_SHARE_ID) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            args.putString("selectAlertString", LocaleController.getString(R.string.SendContactToText));
            args.putString("selectAlertStringGroup", LocaleController.getString(R.string.SendContactToGroupText));
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate(ProfileActivityReplacement.this);
            presentFragment(fragment);
        } else if (id == AB_BOT_UNBLOCK_ID) {
            getMessagesController().unblockPeer(userId, ()-> getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of("/start", userId, null, null, null, false, null, null, null, true, 0, null, false)));
            finishFragment();
        } else if (id == AB_BOT_BLOCK_ID) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            AlertsCreator.createClearOrDeleteDialogAlert(ProfileActivityReplacement.this, false, chat, user, chatEncrypted != null, true, true, (param) -> {
                if (getParentLayout() != null) {
                    List<BaseFragment> fragmentStack = getParentLayout().getFragmentStack();
                    BaseFragment prevFragment = fragmentStack == null || fragmentStack.size() < 2 ? null : fragmentStack.get(fragmentStack.size() - 2);
                    if (prevFragment instanceof ChatActivity) {
                        getParentLayout().removeFragmentFromStack(fragmentStack.size() - 2);
                    }
                }
                finishFragment();
                getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, dialogId, user, chat, param);
            }, getResourceProvider());
        } else if (id == AB_USER_UNBLOCK_ID) {
            getMessagesController().unblockPeer(userId);
            if (BulletinFactory.canShowBulletin(ProfileActivityReplacement.this)) {
                BulletinFactory.createBanBulletin(ProfileActivityReplacement.this, false).show();
            }
        } else if (id == AB_USER_BLOCK_ID) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            if (isReportSpam) {
                AlertsCreator.showBlockReportSpamAlert(ProfileActivityReplacement.this, userId, user, null, chatEncrypted, false, null, param -> {
                    if (param == 1) {
                        getNotificationCenter().removeObserver(ProfileActivityReplacement.this, NotificationCenter.closeChats);
                        getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                        // WIP: playProfileAnimation = 0;
                        finishFragment();
                    } else {
                        getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, userId);
                    }
                }, getResourceProvider());
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
                builder.setTitle(LocaleController.getString(R.string.BlockUser));
                builder.setMessage(AndroidUtilities.replaceTags(formatString("AreYouSureBlockContact2", R.string.AreYouSureBlockContact2, ContactsController.formatName(user.first_name, user.last_name))));
                builder.setPositiveButton(LocaleController.getString(R.string.BlockContact), (dialogInterface, i) -> {
                    getMessagesController().blockPeer(userId);
                    if (BulletinFactory.canShowBulletin(ProfileActivityReplacement.this)) {
                        BulletinFactory.createBanBulletin(ProfileActivityReplacement.this, true).show();
                    }
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) button.setTextColor(getThemedColor(Theme.key_text_RedBold));
            }
        } else if (id == AB_CONTACT_ADD_ID) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            Bundle args = new Bundle();
            args.putLong("user_id", user.id);
            args.putBoolean("addContact", true);
            handleContactAdd(user, args);
        } else if (id == AB_SHARE_ID) {
            TLRPC.User user = userId != 0 ? getMessagesController().getUser(userId) : null;
            TLRPC.Chat chat = chatId != 0 ? getMessagesController().getChat(chatId) : null;
            if (user == null && chat == null) return;
            try {
                String text = "";
                String username = user != null ? UserObject.getPublicUsername(user) : ChatObject.getPublicUsername(chat);
                String about = user != null ? (user.bot && userInfo != null ? userInfo.about : null) : (chatInfo != null ? chatInfo.about : null);
                if (TextUtils.isEmpty(about)) {
                    text = String.format("https://" + getMessagesController().linkPrefix + "/%s", username);
                } else {
                    text = String.format("%s https://" + getMessagesController().linkPrefix + "/%s", about, username);
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.BotShare)), 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (id == AB_GROUP_LEAVE_ID) {
            if (chat == null) return;
            boolean isForum = ChatObject.isForum(chat);
            AlertsCreator.createClearOrDeleteDialogAlert(this, false, chat, null, false, isForum, !isForum, (param) -> {
                // WIP: playProfileAnimation = 0;
                getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                finishFragment();
                getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, -chat.id, null, chat, param);
            }, getResourceProvider());
        } else if (id == AB_SHORTCUT_ID) {
            try {
                long did;
                if (chatEncrypted != null) did = DialogObject.makeEncryptedDialogId(chatEncrypted.id);
                else if (userId != 0) did = userId;
                else if (chatId != 0) did = -chatId;
                else return;
                getMediaDataController().installShortcut(did, MediaDataController.SHORTCUT_TYPE_USER_OR_CHAT);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (id == AB_STATISTICS_ID) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat == null) return;
            presentFragment(StatisticActivity.create(chat, false));
        } else if (id == AB_START_SECRET_CHAT_ID) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(LocaleController.getString(R.string.AreYouSureSecretChatTitle));
            builder.setMessage(LocaleController.getString(R.string.AreYouSureSecretChat));
            builder.setPositiveButton(LocaleController.getString(R.string.Start), (dialogInterface, i) -> {
                if (MessagesController.getInstance(currentAccount).isFrozen()) {
                    AccountFrozenAlert.show(currentAccount);
                    return;
                }
                isCreatingEncryptedChat = true;
                getSecretChatHelper().startSecretChat(getParentActivity(), getMessagesController().getUser(userId));
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        } else if (id == AB_PROFILE_COPY_LINK_ID) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            AndroidUtilities.addToClipboard(getMessagesController().linkPrefix + "/" + UserObject.getPublicUsername(user));
        } else if (id == AB_PROFILE_SET_USERNAME_ID) {
            presentFragment(new ChangeUsernameActivity());
        } else if (id == AB_VIEW_DISCUSSION_ID) {
            if (chatInfo == null || chatInfo.linked_chat_id == 0) return;
            Bundle args = new Bundle();
            args.putLong("chat_id", chatInfo.linked_chat_id);
            if (!getMessagesController().checkCanOpenChat(args, this)) return;
            presentFragment(new ChatActivity(args));
        } else if (id == AB_REPORT_ID) {
            ReportBottomSheet.openChat(this, getDialogId());
        } else if (id == AB_BOT_VIEW_PRIVACY_ID) {
            BotWebViewAttachedSheet.openPrivacy(currentAccount, userId);
        } else if (id == AB_DELETE_TOPIC_ID) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(LocaleController.getPluralString("DeleteTopics", 1));
            TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(chatId, topicId);
            builder.setMessage(formatString("DeleteSelectedTopic", R.string.DeleteSelectedTopic, topic == null ? "topic" : topic.title));
            builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                ArrayList<Integer> topicIds = new ArrayList<>();
                topicIds.add((int) topicId);
                getMessagesController().getTopicsController().deleteTopics(chatId, topicIds);
                // WIP: playProfileAnimation = 0;
                if (parentLayout != null && parentLayout.getFragmentStack() != null) {
                    for (int i = 0; i < parentLayout.getFragmentStack().size(); ++i) {
                        BaseFragment fragment = parentLayout.getFragmentStack().get(i);
                        if (fragment instanceof ChatActivity && ((ChatActivity) fragment).getTopicId() == topicId) {
                            fragment.removeSelfFromStack();
                        }
                    }
                }
                finishFragment();
                Context context = getContext();
                if (context != null) {
                    BulletinFactory.of(Bulletin.BulletinWindow.make(context), getResourceProvider())
                            .createSimpleBulletin(R.raw.ic_delete, LocaleController.getPluralString("TopicsDeleted", 1))
                            .show();
                }
                dialog.dismiss();
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        } else if (id == AB_CHANNEL_STORIES_ID) {
            Bundle args = new Bundle();
            args.putInt("type", MediaActivity.TYPE_ARCHIVED_CHANNEL_STORIES);
            args.putLong("dialog_id", -chatId);
            MediaActivity fragment = new MediaActivity(args, null);
            fragment.setChatInfo(chatInfo);
            presentFragment(fragment);
        } else if (id == AB_SEARCH_MEMBERS_ID) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putInt("type", ChatUsersActivity.TYPE_USERS);
            args.putBoolean("open_search", true);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(chatInfo);
            presentFragment(fragment);
        } else if (id == AB_SEND_GIFTS_ID) {
            if (UserObject.areGiftsDisabled(userInfo)) {
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(getDialogId())))).show();
                }
                return;
            }
            if (chat != null) {
                MessagesController.getGlobalMainSettings().edit().putInt("channelgifthint", 3).apply();
            }
            showDialog(new GiftSheet(getContext(), currentAccount, getDialogId(), null, null));
        }
    }

    private void handleContactAdd(TLRPC.User user, Bundle args) {
        ContactAddActivity contactAddActivity = new ContactAddActivity(args, getResourceProvider());
        contactAddActivity.setDelegate(() -> {
            // WIP: Expand profile & update list
            if (getUndoView() != null) //noinspection deprecation
                getUndoView().showWithAction(dialogId, UndoView.ACTION_CONTACT_ADDED, user);
        });
        presentFragment(contactAddActivity);
    }

}
