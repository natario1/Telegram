package org.telegram.ui;

import android.animation.*;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.*;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_fragment;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Business.OpeningHoursActivity;
import org.telegram.ui.Business.ProfileHoursCell;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.FloatingDebug.FloatingDebugController;
import org.telegram.ui.Components.Paint.PersistColorPalette;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Profile.*;
import org.telegram.ui.Stars.BotStarsActivity;
import org.telegram.ui.Stars.BotStarsController;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoryViewer;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.bots.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Profile.ProfileActivityMenus.*;
import static org.telegram.ui.Profile.ProfileContentView.*;

public class ProfileActivityReplacement extends BaseFragment implements
        SharedMediaLayout.SharedMediaPreloaderDelegate,
        SharedMediaLayout.Delegate,
        ImageUpdater.ImageUpdaterDelegate,
        NotificationCenter.NotificationCenterDelegate,
        DialogsActivity.DialogsActivityDelegate {
    
    private final static int PHONE_OPTION_CALL = 0,
            PHONE_OPTION_COPY = 1,
            PHONE_OPTION_TELEGRAM_CALL = 2,
            PHONE_OPTION_TELEGRAM_VIDEO_CALL = 3;

    private long userId;
    private TLRPC.UserFull userInfo;
    private TL_account.TL_password userPassword;

    private long chatId;
    private TLRPC.ChatFull chatInfo;
    private TLRPC.Chat chat;
    public TLRPC.EncryptedChat chatEncrypted;

    private final LongSparseArray<TLRPC.ChatParticipant> chatMembers = new LongSparseArray<>();
    private final ArrayList<Integer> chatMembersOrder = new ArrayList<>();
    private boolean chatMembersLoading;
    private boolean chatMembersEndReached;
    private int chatMembersOnline = -1;
    private int chatMembersForceShow = 0; // WIP: implement

    private int reportReactionMessageId = 0;
    private long reportReactionFromDialogId = 0;
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
    private boolean isBotInfoLoaded;

    public SharedMediaLayout sharedMediaLayout;
    private ProfileActivityRootLayout rootLayout;
    private ProfileActivityMenus menuHandler;
    private ProfileHeaderView headerView;
    private ProfileContentView listView;

    private float mediaHeaderAnimationProgress = 0F;
    private boolean mediaHeaderVisible = false;
    private AnimatorSet mediaHeaderAnimator;

    private ImageUpdater uploader;
    private TLRPC.FileLocation uploadedAvatarSmall;
    private TLRPC.FileLocation uploadedAvatarBig;

    private ProfileBirthdayEffect.BirthdayEffectFetcher birthdayEffectFetcher;
    private ProfileBirthdayEffect birthdayEffect;
    private boolean birthdayEffectFetcherOwned = false;

    private ProfileChannelCell.ChannelMessageFetcher channelMessageFetcher;
    private boolean channelMessageFetcherSubscribed = false;

    private long banGroupId = 0;
    private TLRPC.ChannelParticipant banGroupParticipant;

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    private FlagSecureReason flagSecure;
    private int versionClickCount = 0;
    private int botPermissionEmojiStatusReqId;
    public final HashSet<Integer> notificationsExceptions = new HashSet<>();
    private final PhotoViewer.PhotoViewerProvider photoViewerProvider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            return handleGetPlaceForPhoto(fileLocation);
        }

        @Override
        public void willHidePhotoViewer() {
            if (headerView == null) return;
            headerView.getAvatar().getImageReceiver().setVisible(true, true);
        }

        @Override
        public void openPhotoForEdit(String file, String thumb, boolean isVideo) {
            uploader.openPhotoForEdit(file, thumb, 0, isVideo);
        }
    };

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
        ProfileBirthdayEffect.BirthdayEffectFetcher birthdayEffectFetcher
    ) {
        this.channelMessageFetcher = channelMessageFetcher;
        this.birthdayEffectFetcher = birthdayEffectFetcher;
        this.birthdayEffectFetcherOwned = false;
    }

    public void setUserInfo(TLRPC.UserFull value) {
        this.userInfo = value;
        if (sharedMediaLayout != null) {
            sharedMediaLayout.setUserInfo(value);
        }
        if (uploader == null) {
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
        if (!isSettings()) {
            if (channelMessageFetcher == null) {
                channelMessageFetcher = new ProfileChannelCell.ChannelMessageFetcher(currentAccount);
            }
            if (!channelMessageFetcherSubscribed && userInfo != null) {
                channelMessageFetcherSubscribed = true;
                channelMessageFetcher.subscribe(() -> updateListData("channelMessageFetcher"));
                channelMessageFetcher.fetch(userInfo);
            }
            ProfileBirthdayEffect.BirthdayEffectFetcher old = birthdayEffectFetcher;
            birthdayEffectFetcher = ProfileBirthdayEffect.BirthdayEffectFetcher.of(currentAccount, userInfo, old);
            birthdayEffectFetcherOwned = birthdayEffectFetcher != old;
            if (birthdayEffectFetcher != null) {
                birthdayEffectFetcher.subscribe(this::updateBirthdayData);
            }
        }
        if (headerView != null) {
            headerView.getAvatar().updateStories();
        }
        updateTtlData();
        updatePremiumData();
        updateListData("setUserInfo");
    }

    public TLRPC.User getCurrentUser() {
        return userInfo == null ? null : userInfo.user;
    }

    public void setChatInfo(TLRPC.ChatFull value) {
        if (chatInfo instanceof TLRPC.TL_channelFull && value != null && value.participants == null) {
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
        updateOnlineData(false);
        updateListData("setChatInfo");
        if (headerView != null) {
            headerView.getAvatar().updateStories();
        }
        if (menuHandler != null) {
            boolean canPurchase = !BuildVars.IS_BILLING_UNAVAILABLE && !getMessagesController().premiumPurchaseBlocked();
            boolean canSendGifts = chatInfo != null && chatInfo.stargifts_available;
            menuHandler.updateSendGiftsItem(canPurchase && canSendGifts);
        }
    }

    @Override
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

    public long getUserId() {
        return userId;
    }

    public long getTopicId() {
        return topicId;
    }

    public long getChatId() {
        return chatId;
    }

    public boolean isSettings() {
        return uploader != null && !isMyProfile;
    }

    public boolean isMyProfile() {
        return isMyProfile;
    }

    public boolean isSaved() {
        return isSaved;
    }

    public boolean isTopic() {
        return topicId != 0;
    }

    // UPDATE*

    private void updateProfileData(boolean reload) {
        if (getParentActivity() == null) return;
        TLRPC.EmojiStatus peerColorEmojiStatus = null;
        int peerColorFallbackId = 0;
        long profileEmojiId = 0;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            peerColorEmojiStatus = user.emoji_status;
            peerColorFallbackId = UserObject.getProfileColorId(user);
            profileEmojiId = UserObject.getProfileEmojiId(user);
            if (headerView != null) {
                headerView.setAvatarUser(user, userInfo, uploadedAvatarSmall, uploadedAvatarBig);
            }
            if (menuHandler != null) {
                menuHandler.updateUsernameRelatedItems(UserObject.getPublicUsername(user) != null);
            }
        } else if (chatId != 0) {
            TLRPC.Chat newChat = getMessagesController().getChat(chatId);
            chat = newChat != null ? newChat : chat;
            if (chat == null) return;
            peerColorEmojiStatus = chat.emoji_status;
            peerColorFallbackId = ChatObject.getProfileColorId(chat);
            profileEmojiId = ChatObject.getProfileEmojiId(chat);
            headerView.setAvatarChat(chat, topicId, uploadedAvatarBig);

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
            updateColors(true);
        }
        if (headerView != null) {
            headerView.setEmojiInfo(profileEmojiId, peerColorEmojiStatus, true);
        }
        if (menuHandler != null) {
            menuHandler.setQrItemNeeded(true, true);
        }
    }

    private void updateColors(boolean animated) {
        if (rootLayout != null) {
            rootLayout.updateColors(peerColor, mediaHeaderAnimationProgress);
        }
        if (menuHandler != null) {
            menuHandler.updateColors(peerColor, mediaHeaderAnimationProgress);
        }
        if (headerView != null) {
            headerView.updateColors(peerColor, animated);
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
        boolean editMenuItemNeeded = false;
        boolean appendLogout = false;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return;
            if (UserObject.isUserSelf(user)) {
                editMenuItemNeeded = isMyProfile;
                appendLogout = !isMyProfile;
                menuHandler.appendMainMenuSubItem(AB_EDIT_INFO_ID);
                if (uploader != null) menuHandler.appendMainMenuSubItem(AB_ADD_PHOTO_ID);
                menuHandler.appendMainMenuSubItem(AB_EDIT_COLOR_ID);
                updatePremiumData(); // AB_EDIT_COLOR_ID has different icons
                if (isMyProfile) {
                    menuHandler.appendMainMenuSubItem(AB_PROFILE_COPY_LINK_ID);
                    menuHandler.appendMainMenuSubItem(AB_PROFILE_SET_USERNAME_ID);
                    menuHandler.updateUsernameRelatedItems(UserObject.getPublicUsername(user) != null);
                }
            } else {
                editMenuItemNeeded = user.bot && user.bot_can_edit;
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
                    editMenuItemNeeded = ChatObject.canManageTopic(currentAccount, chat, topicId);
                } else {
                    editMenuItemNeeded = ChatObject.hasAdminRights(chat) || chat.megagroup && ChatObject.canChangeChatInfo(chat);
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
                editMenuItemNeeded = ChatObject.canChangeChatInfo(chat);
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
        menuHandler.setEditItemNeeded(editMenuItemNeeded, animated);
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

    private void updateLandscapeData() {
        if (headerView != null) {
            final Point size = new Point();
            final Display display = getParentActivity().getWindowManager().getDefaultDisplay();
            display.getSize(size);
            headerView.setDisplaySize(size);
        }
    }

    private void updateBirthdayData() {
        if (rootLayout == null || !isFullyVisible || birthdayEffectFetcher == null) return;
        if (birthdayEffect != null) {
            birthdayEffect.updateFetcher(birthdayEffectFetcher);
            birthdayEffect.invalidate();
        } else {
            birthdayEffect = new ProfileBirthdayEffect(getContext(), outPoint -> {
                TextDetailCell cell = listView != null ? listView.findRowView(Rows.InfoBirthday) : null;
                if (cell != null) {
                    outPoint.x = listView.getX() + cell.getX() + cell.textView.getX() + dp(12);
                    outPoint.y = listView.getY() + cell.getY() + cell.textView.getY() + cell.textView.getMeasuredHeight() / 2f;
                }
            }, birthdayEffectFetcher);
            rootLayout.addView(birthdayEffect, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));
        }
    }

    private void updateNotificationExceptionsData() {
        if (!isTopic() && ChatObject.isForum(chat)) {
            getNotificationsController().loadTopicsNotificationsExceptions(-chatId, (topics) -> {
                ArrayList<Integer> arrayList = new ArrayList<>(topics);
                for (int i = 0; i < arrayList.size(); i++) {
                    if (getMessagesController().getTopicsController().findTopic(chatId, arrayList.get(i)) == null) {
                        arrayList.remove(i);
                        i--;
                    }
                }
                notificationsExceptions.clear();
                notificationsExceptions.addAll(arrayList);
                // WIP: Notify header instead. if (listView != null) listView.notifyRowChange(Rows.Notifications);
            });
        }
    }

    private void updateChatMembersData(boolean reload) {
        if (chatInfo == null || chat == null || isTopic()) return;
        if (!chat.megagroup) {
            // When !megagroup, just load from chatInfo
            chatMembers.clear();
            if (chatInfo.participants != null && !(chatInfo.participants instanceof TLRPC.TL_chatParticipantsForbidden)) {
                for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                    TLRPC.ChatParticipant chatParticipant = chatInfo.participants.participants.get(a);
                    chatMembers.put(chatParticipant.user_id, chatParticipant);
                }
            }
            updateOnlineData(true);
            return;
        }
        if (chatMembersLoading || (!reload && chatMembersEndReached)) return;
        chatMembersLoading = true;
        chatMembersEndReached = false;
        final int delay = !chatMembers.isEmpty() && reload ? 300 : 0;

        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = getMessagesController().getInputChannel(chatId);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = reload ? 0 : chatMembers.size();
        req.limit = 200;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> getNotificationCenter().doOnIdle(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                if (res.users.size() < 200) {
                    chatMembersEndReached = true;
                }
                if (req.offset == 0) {
                    chatMembers.clear();
                    chatInfo.participants = new TLRPC.TL_chatParticipants();
                    getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                    getMessagesStorage().updateChannelUsers(chatId, res.participants);
                }
                for (int a = 0; a < res.participants.size(); a++) {
                    TLRPC.TL_chatChannelParticipant participant = new TLRPC.TL_chatChannelParticipant();
                    participant.channelParticipant = res.participants.get(a);
                    participant.inviter_id = participant.channelParticipant.inviter_id;
                    participant.user_id = MessageObject.getPeerId(participant.channelParticipant.peer);
                    participant.date = participant.channelParticipant.date;
                    if (chatMembers.indexOfKey(participant.user_id) < 0) {
                        if (chatInfo.participants == null) {
                            chatInfo.participants = new TLRPC.TL_chatParticipants();
                        }
                        chatInfo.participants.participants.add(participant);
                        chatMembers.put(participant.user_id, participant);
                    }
                }
            }
            chatMembersLoading = false;
            updateOnlineData(false);
            updateListData("updateChatMembersData");
        }), delay));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void updateListData(String reason) {
        if (listView == null) return;
        listView.updateRows(old -> {
            Rows rows = new Rows();
            boolean hasMedia = false;
            if (sharedMediaPreloader != null) {
                int[] lastMediaCount = sharedMediaPreloader.getLastMediaCount();
                hasMedia = Arrays.stream(lastMediaCount).anyMatch(i -> i > 0) || sharedMediaPreloader.hasSavedMessages || sharedMediaPreloader.hasPreviews;
            }
            if (!hasMedia && userInfo != null) {
                hasMedia = userInfo.stories_pinned_available;
            }
            if (!hasMedia && userInfo != null && userInfo.bot_info != null) {
                hasMedia = userInfo.bot_info.has_preview_medias;
            }
            if (!hasMedia && (userInfo != null && userInfo.stargifts_count > 0 || chatInfo != null && chatInfo.stargifts_count > 0)) {
                hasMedia = true;
            }
            if (!hasMedia && chatInfo != null) {
                hasMedia = chatInfo.stories_pinned_available;
            }
            if (!hasMedia) {
                if (chatId != 0 && MessagesController.ChannelRecommendations.hasRecommendations(currentAccount, -chatId)) {
                    hasMedia = true;
                } else if (userInfo != null && userInfo.user != null && userInfo.user.bot && userId != 0 && MessagesController.ChannelRecommendations.hasRecommendations(currentAccount, userId)) {
                    hasMedia = true;
                }
            }

            if (userId != 0) {
                TLRPC.User user = getMessagesController().getUser(userId);
                if (UserObject.isUserSelf(user) && !isMyProfile) {
                    /* WIP: if (avatarBig == null && (user.photo == null || !(user.photo.photo_big instanceof TLRPC.TL_fileLocation_layer97) && !(user.photo.photo_big instanceof TLRPC.TL_fileLocationToBeDeprecated)) && (avatarsViewPager == null || avatarsViewPager.getRealCount() == 0)) {
                        Rows.SetAvatar = rowCount++;
                        Rows.SetAvatarSection = rowCount++;
                    } */
                    // Settings screen
                    rows.append(Rows.MyHeader);
                    rows.append(Rows.MyPhoneNumber);
                    rows.append(Rows.MyUsername);
                    rows.append(Rows.MyBio);
                    rows.append(Rows.MyFooter);
                    rows.append(Rows.MyShadow);

                    Set<String> suggestions = getMessagesController().pendingSuggestions;
                    if (suggestions.contains("PREMIUM_GRACE")) {
                        rows.append(Rows.SuggestionGrace);
                        rows.append(Rows.SuggestionShadow);
                    } else if (suggestions.contains("VALIDATE_PHONE_NUMBER")) {
                        rows.append(Rows.SuggestionPhone);
                        rows.append(Rows.SuggestionShadow);
                    } else if (suggestions.contains("VALIDATE_PASSWORD")) {
                        rows.append(Rows.SuggestionPassword);
                        rows.append(Rows.SuggestionShadow);
                    }

                    rows.append(Rows.MySettingsHeader);
                    rows.append(Rows.MySettingsChat);
                    rows.append(Rows.MySettingsPrivacy);
                    rows.append(Rows.MySettingsNotification);
                    rows.append(Rows.MySettingsData);
                    rows.append(Rows.MySettingsLiteMode);
                    if (getMessagesController().filtersEnabled || !getMessagesController().dialogFilters.isEmpty()) {
                        rows.append(Rows.MySettingsFilters);
                    }
                    rows.append(Rows.MySettingsDevices);
                    rows.append(Rows.MySettingsLanguage);
                    rows.append(Rows.MySettingsSection);
                    if (!getMessagesController().premiumFeaturesBlocked()) rows.append(Rows.FeaturesPremium);
                    if (getMessagesController().starsPurchaseAvailable()) rows.append(Rows.FeaturesStars);
                    if (!getMessagesController().premiumFeaturesBlocked()) rows.append(Rows.FeaturesBusiness);
                    if (!getMessagesController().premiumPurchaseBlocked()) rows.append(Rows.FeaturesGift);
                    if (rows.has(Rows.FeaturesPremium) || rows.has(Rows.FeaturesStars) || rows.has(Rows.FeaturesBusiness) || rows.has(Rows.FeaturesGift)) {
                        rows.append(Rows.FeaturesSection);
                    }
                    rows.append(Rows.HelpHeader);
                    rows.append(Rows.HelpQuestion);
                    rows.append(Rows.HelpFaq);
                    rows.append(Rows.HelpPolicy);
                    if (BuildVars.LOGS_ENABLED || BuildVars.DEBUG_PRIVATE_VERSION) {
                        rows.append(Rows.HelpSection);
                        rows.append(Rows.DebugHeader);
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        rows.append(Rows.DebugSendLogs);
                        rows.append(Rows.DebugSendLastLogs);
                        rows.append(Rows.DebugClearLogs);
                    }
                    if (BuildVars.DEBUG_VERSION) {
                        rows.append(Rows.DebugSwitchBackend);
                    }
                    rows.append(Rows.MyVersion);
                    hasMedia = false;
                } else {
                    String username = UserObject.getPublicUsername(user);
                    boolean hasInfo = userInfo != null && !TextUtils.isEmpty(userInfo.about) || user != null && !TextUtils.isEmpty(username);
                    boolean hasPhone = user != null && (!TextUtils.isEmpty(user.phone) || !TextUtils.isEmpty(PhoneFormat.stripExceptNumbers(arguments.getString("vcard_phone"))));

                    if (userInfo != null && (userInfo.flags2 & 64) != 0 && (channelMessageFetcher == null || !channelMessageFetcher.loaded || channelMessageFetcher.messageObject != null)) {
                        final TLRPC.Chat channel = getMessagesController().getChat(userInfo.personal_channel_id);
                        if (channel != null && (ChatObject.isPublic(channel) || !ChatObject.isNotInChat(channel))) {
                            rows.appendObject(Rows.PersonalChannel, channelMessageFetcher != null ? channelMessageFetcher.messageObject : null, 1);
                            rows.append(Rows.PersonalChannelShadow);
                        }
                    }
                    rows.append(Rows.InfoHeader);
                    if ((user != null && !user.bot) && (hasPhone || !hasInfo)) rows.append(Rows.InfoPhone);
                    if (userInfo != null && !TextUtils.isEmpty(userInfo.about)) rows.appendObject(Rows.InfoUserAbout, user, 1);
                    if (user != null && username != null) rows.appendObject(Rows.InfoUsername, user, 1);
                    if (userInfo != null) {
                        if (userInfo.birthday != null) rows.appendObject(Rows.InfoBirthday, userInfo.birthday, 1);
                        if (userInfo.business_work_hours != null) rows.appendObject(Rows.InfoBizHours, userInfo.business_work_hours, 1);
                        if (userInfo.business_location != null) rows.appendObject(Rows.InfoBizLocation, userInfo.business_location, 1);
                    }
                    // WIP: Moved to header if (userId != getUserConfig().getClientUserId()) rows.append(Rows.Notifications);
                    if (user != null && user.bot && user.bot_has_main_app) rows.append(Rows.InfoBotApp);
                    rows.append(Rows.InfoFooter);
                    rows.appendObject(Rows.InfoShadow, user, 1);

                    if (user != null && user.bot && userInfo != null && userInfo.starref_program != null && (userInfo.starref_program.flags & 2) == 0 && getMessagesController().starrefConnectAllowed) {
                        rows.append(Rows.Affiliate);
                        rows.appendObject(Rows.AffiliateInfo, user, 1);
                    }

                    if (user != null && user.bot) {
                        BotLocation botLocation = old.payload(Rows.BotPermissionLocation);
                        BotBiometry botBiometry = old.payload(Rows.BotPermissionBiometry);
                        if (botLocation == null && getContext() != null) botLocation = BotLocation.get(getContext(), currentAccount, userId);
                        if (botBiometry == null && getContext() != null) botBiometry = BotBiometry.get(getContext(), currentAccount, userId);
                        final boolean containsPermissionLocation = botLocation != null && botLocation.asked();
                        final boolean containsPermissionBiometry = botBiometry != null && botBiometry.asked();
                        final boolean containsPermissionEmojiStatus = userInfo != null && userInfo.bot_can_manage_emoji_status || SetupEmojiStatusSheet.getAccessRequested(getContext(), currentAccount, userId);
                        if (containsPermissionEmojiStatus || containsPermissionLocation || containsPermissionBiometry) {
                            rows.append(Rows.BotPermissionHeader);
                            if (containsPermissionEmojiStatus) rows.append(Rows.BotPermissionEmojiStatus);
                            if (containsPermissionLocation) rows.appendObject(Rows.BotPermissionLocation, botLocation, 1);
                            if (containsPermissionBiometry) rows.appendObject(Rows.BotPermissionBiometry, botBiometry, 1);
                            rows.append(Rows.BotPermissionShadow);
                        }
                    }

                    if (chatEncrypted instanceof TLRPC.TL_encryptedChat) {
                        rows.append(Rows.SecretSettingsTimer);
                        rows.append(Rows.SecretSettingsKey);
                        rows.append(Rows.SecretSettingsShadow);
                    }

                    if (user != null && !user.bot && chatEncrypted == null && user.id != getUserConfig().getClientUserId() && isUserBlocked) {
                        rows.append(Rows.Unblock);
                        rows.append(Rows.UnblockShadow);
                    }

                    boolean divider = false;
                    if (user != null && user.bot) {
                        BotStarsController controller = BotStarsController.getInstance(currentAccount);
                        if (userInfo != null && userInfo.can_view_revenue && controller.getTONBalance(userId) > 0) rows.append(Rows.ActionsBotTonBalance);
                        if (controller.getBotStarsBalance(userId).amount > 0 || controller.hasTransactions(userId)) rows.append(Rows.ActionsBotStarsBalance);
                    }
                    if (user != null && user.bot && !user.bot_nochats) {
                        rows.append(Rows.ActionsAddToGroupButton);
                        rows.append(Rows.ActionsAddToGroupInfo);
                    } else if (rows.has(Rows.ActionsBotStarsBalance)) {
                        divider = true;
                    }
                    boolean showAddToContacts = arguments.getBoolean("show_add_to_contacts", true);
                    if (!isMyProfile && showAddToContacts && user != null && !user.contact && !user.bot && !UserObject.isService(user.id)) {
                        rows.append(Rows.ActionsAddToContacts);
                        divider = true;
                    }
                    if (!isMyProfile && reportReactionMessageId != 0 && !ContactsController.getInstance(currentAccount).isContact(userId)) {
                        rows.appendObject(Rows.ActionsReportReaction, reportReactionFromDialogId, 1);
                        divider = true;
                    }
                    if (divider) rows.append(Rows.ActionsShadow);

                    hasMedia = hasMedia || (user != null && user.bot && user.bot_can_edit) || userInfo != null && userInfo.common_chats_count != 0 || isMyProfile;
                    /* WIP moving to header: else if (!rows.has(Rows.LastSection) && needSendMessage) {
                        rows.append(Rows.SendMessage);
                        rows.append(Rows.Report);
                        rows.append(Rows.LastSection);
                    } */
                }
            } else if (isTopic()) {
                rows.append(Rows.InfoHeader);
                rows.appendObject(Rows.InfoUsername, getCurrentChat(), 1);
                // WIP moving to header: rows.append(Rows.NotificationsSimple);
                rows.append(Rows.InfoFooter);
                rows.append(Rows.InfoShadow);
            } else if (chatId != 0) {
                if (chatInfo != null && (!TextUtils.isEmpty(chatInfo.about) || chatInfo.location instanceof TLRPC.TL_channelLocation) || ChatObject.isPublic(chat)) {
                    rows.append(Rows.InfoHeader);
                    if (chatInfo != null) {
                        if (!TextUtils.isEmpty(chatInfo.about)) rows.appendObject(Rows.InfoChatAbout, chat, 1);
                        if (chatInfo.location instanceof TLRPC.TL_channelLocation) rows.append(Rows.InfoLocation);
                    }
                    if (ChatObject.isPublic(chat)) rows.appendObject(Rows.InfoUsername, chat, 1);
                    rows.append(Rows.InfoFooter);
                    rows.append(Rows.InfoShadow);
                }
                // WIP: moved to header rows.append(Rows.Notifications);

                BotStarsController bots = BotStarsController.getInstance(currentAccount);
                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                    if (chatInfo != null && (chat.creator || chatInfo.can_view_participants)) {
                        rows.append(Rows.ChannelOptionsSubscribers);
                        if (chatInfo.requests_pending > 0) rows.append(Rows.ChannelOptionsSubscribersRequests);
                        rows.append(Rows.ChannelOptionsAdministrators);
                        if (chatInfo.banned_count != 0 || chatInfo.kicked_count != 0) rows.append(Rows.ChannelOptionsBlockedUsers);
                        if (chatInfo != null && chatInfo.can_view_stars_revenue && (bots.getBotStarsBalance(-chatId).amount > 0 || bots.hasTransactions(-chatId))
                                || chatInfo != null && chatInfo.can_view_revenue && bots.getTONBalance(-chatId) > 0) {
                            rows.append(Rows.ChannelOptionsBalance);
                        }
                        rows.append(Rows.ChannelOptionsSettings);
                        rows.append(Rows.ChannelOptionsShadow);
                    }
                } else {
                    if (chatInfo != null && chatInfo.can_view_stars_revenue && (bots.getBotStarsBalance(-chatId).amount > 0 || bots.hasTransactions(-chatId))
                            || chatInfo != null && chatInfo.can_view_revenue && bots.getTONBalance(-chatId) > 0) {
                        rows.append(Rows.ChannelOptionsBalance);
                        rows.append(Rows.ChannelOptionsShadow);
                    }
                }

                if (ChatObject.isChannel(chat)) {
                    // Channels
                    if (!isTopic() && chatInfo != null && chat.megagroup && !chatMembers.isEmpty()) {
                        if (!ChatObject.isNotInChat(chat) && ChatObject.canAddUsers(chat) && chatInfo.participants_count < getMessagesController().maxMegagroupCount) {
                            rows.append(Rows.MembersAdd);
                        }
                        int count = chatMembers.size();
                        if ((count <= 5 || !hasMedia || chatMembersForceShow == 1) && chatMembersForceShow != 2) {
                            rows.appendList(Rows.Members, sortMembers());
                            chatMembersForceShow = 1;
                            if (sharedMediaLayout != null) {
                                sharedMediaLayout.setChatUsers(null, null);
                            }
                        } else {
                            if (rows.has(Rows.MembersAdd)) rows.append(Rows.MembersShadow);
                            if (sharedMediaLayout != null) {
                                if (!chatMembersOrder.isEmpty()) {
                                    chatMembersForceShow = 2;
                                }
                                sharedMediaLayout.setChatUsers(sortMembers(), chatInfo);
                            }
                        }
                    } else {
                        if (!ChatObject.isNotInChat(chat) && ChatObject.canAddUsers(chat) && chatInfo != null && chatInfo.participants_hidden) {
                            rows.append(Rows.MembersAdd);
                            rows.append(Rows.MembersShadow);
                        }
                        if (sharedMediaLayout != null) {
                            sharedMediaLayout.updateAdapters();
                        }
                    }

                    /* WIP: moved to header if (!rows.has(Rows.UnblockShadow) && chat != null && chat.left && !chat.kicked) {
                        long requestedTime = MessagesController.getNotificationsSettings(currentAccount).getLong("dialog_join_requested_time_" + dialogId, -1);
                        if (!(requestedTime > 0 && System.currentTimeMillis() - requestedTime < 1000 * 60 * 2)) {
                            rows.append(Rows.Join);
                            rows.append(Rows.JoinShadow);
                        }
                    } */
                } else if (chatInfo != null) {
                    // Groups
                    if (!isTopic() && chatInfo.participants != null && chatInfo.participants.participants != null && !(chatInfo.participants instanceof TLRPC.TL_chatParticipantsForbidden)) {
                        if (chat != null && (ChatObject.canAddUsers(chat) || chat.default_banned_rights == null || !chat.default_banned_rights.invite_users)) {
                            rows.append(Rows.MembersAdd);
                        }
                        int count = chatMembers.size();
                        if (count <= 5 || !hasMedia) {
                            rows.appendList(Rows.Members, sortMembers());
                            if (sharedMediaLayout != null) {
                                sharedMediaLayout.setChatUsers(null, null);
                            }
                        } else {
                            if (!rows.has(Rows.MembersAdd)) rows.append(Rows.MembersShadow);
                            if (sharedMediaLayout != null) {
                                sharedMediaLayout.setChatUsers(sortMembers(), chatInfo);
                            }
                        }
                    } else {
                        if (!ChatObject.isNotInChat(chat) && ChatObject.canAddUsers(chat) && chatInfo.participants_hidden) {
                            rows.append(Rows.MembersAdd);
                            rows.append(Rows.MembersShadow);
                        }
                        if (sharedMediaLayout != null) {
                            sharedMediaLayout.updateAdapters();
                        }
                    }
                }
            }
            if (hasMedia) {
                if (rows.count() == 0) rows.append(Rows.SharedMediaPrefix);
                rows.append(Rows.SharedMedia);
            } else {
                rows.append(Rows.Filler);
            }
            listView.setTranslateSelectorPosition(rows.position(Rows.InfoBizHours));
            return rows;
        });
    }

    private List<TLRPC.ChatParticipant> sortMembers() {
        int count = chatMembers.size();
        ArrayList<TLRPC.ChatParticipant> sorted = new ArrayList<>(Collections.nCopies(count, null));
        for (int i = 0; i < count; i++) {
            int index = chatMembersOrder.size() > i ? chatMembersOrder.get(i) : i;
            if (index >= 0 && index < count) sorted.set(index, chatMembers.valueAt(i));
        }
        return sorted.stream().filter(Objects::nonNull).toList();
    }

    // Needed whenever chatMembers changes.
    private void updateOnlineData(boolean notify) {
        chatMembersOnline = 0;
        int currentTime = getConnectionsManager().getCurrentTime();
        chatMembersOrder.clear();
        if (chatInfo instanceof TLRPC.TL_chatFull || chatInfo instanceof TLRPC.TL_channelFull && chatInfo.participants_count <= 200 && !chatMembers.isEmpty()) {
            final ArrayList<Integer> sortNum = new ArrayList<>();
            for (int a = 0; a < chatMembers.size(); a++) {
                TLRPC.ChatParticipant participant = chatMembers.valueAt(a);
                TLRPC.User user = getMessagesController().getUser(participant.user_id);
                if (user != null && user.status != null && (user.status.expires > currentTime || user.id == getUserConfig().getClientUserId()) && user.status.expires > 10000) {
                    chatMembersOnline++;
                }
                chatMembersOrder.add(a);
                int sort = Integer.MIN_VALUE;
                if (user != null) {
                    if (user.bot) {
                        sort = -110;
                    } else if (user.self) {
                        sort = currentTime + 50000;
                    } else if (user.status != null) {
                        sort = user.status.expires;
                    }
                }
                sortNum.add(sort);
            }

            try {
                Collections.sort(chatMembersOrder, Comparator.comparingInt(hs -> sortNum.get((int) hs)).reversed());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (notify && listView != null) {
                listView.updateRows((rows) -> rows.copy((row, kind) -> {
                    if (kind == Rows.Members) {
                        row.appendList(kind, sortMembers());
                        return false;
                    }
                    return true;
                }));
            }
            if (sharedMediaLayout != null && listView != null && listView.getRowPosition(Rows.SharedMedia) >= 0) {
                if ((chatMembersOrder.size() > 5 || chatMembersForceShow == 2) && chatMembersForceShow != 1) {
                    sharedMediaLayout.setChatUsers(sortMembers(), chatInfo);
                }
            }
        } else if (chatInfo instanceof TLRPC.TL_channelFull && chatInfo.participants_count > 200) {
            chatMembersOnline = chatInfo.online_count;
        }
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
        reportReactionMessageId = arguments.getInt("report_reaction_message_id", 0);
        reportReactionFromDialogId = arguments.getLong("report_reaction_from_dialog_id", 0);
        banGroupId = arguments.getLong("ban_chat_id", 0);
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
                TLRPC.UserFull newInfo = getMessagesController().getUserFull(userId);
                setUserInfo(newInfo);
            }
            getMessagesController().loadFullUser(user, classGuid, true);
            if (UserObject.isUserSelf(user)) {
                uploader = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
                uploader.setOpenWithFrontfaceCamera(true);
                uploader.parentFragment = this;
                uploader.setDelegate(this);
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
                TLRPC.ChatFull newInfo = getMessagesController().getChatFull(chatId);
                setChatInfo(newInfo);
            }
            if (ChatObject.isChannel(chat)) {
                getMessagesController().loadFullChat(chatId, classGuid, true);
            } else if (chatInfo == null) {
                TLRPC.ChatFull newInfo = getMessagesStorage().loadChatInfo(chatId, false, null, false, false);
                setChatInfo(newInfo);
            }
            updateChatMembersData(true);
            updateNotificationExceptionsData();
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
        if (uploader != null) {
            uploader.clear();
        }
        if (birthdayEffectFetcher != null && birthdayEffectFetcherOwned) {
            birthdayEffectFetcherOwned = false;
            birthdayEffectFetcher.detach(true);
            birthdayEffectFetcher = null;
        }
        if (headerView != null) {
            headerView.unsetAvatar();
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
        if (uploader != null) {
            uploader.onResume();
            setParentActivityTitle(getString(R.string.Settings));
        }
        checkMediaHeaderVisible();
        updateProfileData(true);
        updateLandscapeData();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (rootLayout != null) {
            rootLayout.hideUndoView(0);
        }
        if (uploader != null) {
            uploader.onPause();
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
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        updateBirthdayData();
    }

    @Override
    public boolean canBeginSlide() {
        if (!sharedMediaLayout.isSwipeBackEnabled()) return false;
        return super.canBeginSlide();
    }

    @Override
    public void dismissCurrentDialog() {
        if (uploader != null && uploader.dismissCurrentDialog(visibleDialog)) return;
        super.dismissCurrentDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return (uploader == null || uploader.dismissDialogOnPause(dialog)) && super.dismissDialogOnPause(dialog);
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
        if (uploader != null) {
            uploader.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (uploader != null) {
            uploader.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
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
        if (uploader != null && uploader.currentPicturePath != null) {
            args.putString("path", uploader.currentPicturePath);
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {
        if (uploader != null) {
            uploader.currentPicturePath = args.getString("path");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (sharedMediaLayout != null) {
            sharedMediaLayout.onConfigurationChanged(newConfig);
        }
        updateLandscapeData();
    }

    // TRANSITIONS

    private void checkMediaHeaderVisible() {
        // WIP: if (openAnimationInProgress) return;
        boolean searchVisible = uploader == null && actionBar != null && actionBar.isSearchFieldVisible();
        if (!searchVisible) {
            View sharedMedia = listView == null ? null : listView.findRowView(Rows.SharedMedia);
            setMediaHeaderVisible(sharedMedia != null && sharedMedia.getTop() <= 0);
        } else {
            setMediaHeaderVisible(true);
        }
        if (headerView != null && menuHandler != null) {
            int growth = headerView.getGrowth();
            menuHandler.setQrItemDisplayable(growth > dp(72), true);
        }
    }

    private void setMediaHeaderVisible(boolean visible) {
        if (visible == this.mediaHeaderVisible || sharedMediaLayout == null) return;
        this.mediaHeaderVisible = visible;
        if (mediaHeaderAnimator != null) mediaHeaderAnimator.cancel();

        // WIP: updateStoriesViewBounds(false);
        if (actionBar != null) actionBar.createMenu().requestLayout();

        ArrayList<Animator> animators = new ArrayList<>();
        if (menuHandler != null) {
            menuHandler.setActionMode(visible, sharedMediaLayout, animators);
        }
        ValueAnimator callbackAnimator = ValueAnimator.ofFloat(mediaHeaderAnimationProgress, visible ? 1.0f : 0.0f);
        callbackAnimator.addUpdateListener(animation -> {
            // ACTIONBAR_HEADER_PROGRESS
            mediaHeaderAnimationProgress = (float) animation.getAnimatedValue();
            if (headerView != null) headerView.setActionModeProgress(mediaHeaderAnimationProgress);
            // WIP: if (storyView != null) storyView.setActionBarActionMode(value);
            updateColors(false);
        });
        animators.add(callbackAnimator);
        // WIP: animators.add(ObjectAnimator.ofFloat(onlineTextView[1], View.ALPHA, visible ? 0.0f : 1.0f));
        // WIP: if (myProfile) animators.add(ObjectAnimator.ofFloat(onlineTextView[3], View.ALPHA, visible ? 0.0f : 1.0f));
        // WIP: animators.add(ObjectAnimator.ofFloat(mediaCounterTextView, View.ALPHA, visible ? 1.0f : 0.0f));
        // WIP: animation for header shadow alpha => 0 (HEADER_SHADOW, when mediaHeader)
        /* WIP: if (storyView != null) {
            ValueAnimator va = ValueAnimator.ofFloat(0, 1);
            va.addUpdateListener(a -> updateStoriesViewBounds(true));
            animators.add(va);
        } */
        mediaHeaderAnimator = new AnimatorSet();
        mediaHeaderAnimator.playTogether(animators);
        mediaHeaderAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        // WIP: delayed animation for header shadow alpha => 1 (HEADER_SHADOW, when !mediaHeader)
        mediaHeaderAnimator.setDuration(150);
        mediaHeaderAnimator.start();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        this.isOpen = isOpen;
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
            checkMediaHeaderVisible();
        }
    }


    // MISC

    public void updateGifts() {
        if (headerView != null) headerView.updateGifts();
    }

    public void prepareBlurBitmap() {
        if (rootLayout != null) rootLayout.prepareBlurredView();
    }

    // SHARED MEDIA

    @Override
    public void mediaCountUpdated() {
        if (sharedMediaLayout != null && sharedMediaPreloader != null) {
            sharedMediaLayout.setNewMediaCounts(sharedMediaPreloader.getLastMediaCount());
        }
        updateListData("mediaCountUpdated");
        updateSelectedMediaTabText();
        if (userInfo != null) {
            resumeDelayedFragmentAnimation();
        }
    }

    @Override
    public void scrollToSharedMedia() {
        scrollToSharedMedia(false);
    }

    public void scrollToSharedMedia(boolean animated) {
        if (listView != null) listView.scrollToRow(Rows.SharedMedia, animated);
    }

    @Override
    public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean isLong, boolean resultOnly, View view) {
        return handleMemberPress(participant, isLong, resultOnly, view);
    }

    @Override
    public RecyclerListView getListView() {
        return listView;
    }

    @Override
    public boolean canSearchMembers() {
        return menuHandler != null && menuHandler.hasMainMenuSubItem(AB_SEARCH_MEMBERS_ID);
    }

    @Override
    public void updateSelectedMediaTabText() {
        // WIP
    }

    @Override
    public boolean isFragmentOpened() {
        return isOpen;
    }

    // VIEW HIERARCHY

    @Override
    public boolean isActionBarCrossfadeEnabled() {
        return actionBar != null && (headerView == null || !headerView.canGrow());
    }

    @Override
    public boolean isLightStatusBar() {
        int color;
        if (headerView != null && !headerView.canGrow()) {
            return false;
        }
        if (actionBar.isActionModeShowed()) {
            color = getThemedColor(Theme.key_actionBarActionModeDefault);
        } else if (mediaHeaderVisible) {
            color = getThemedColor(Theme.key_windowBackgroundWhite);
        } else if (peerColor != null) {
            color = peerColor.getBgColor2(Theme.isCurrentThemeDark());
        } else {
            color = getThemedColor(Theme.key_actionBarDefault);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

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

    @Override
    public View createView(Context context) {
        // Preparation
        Theme.createProfileResources(context);
        Theme.createChatResources(context, false);
        checkThemeResourceProvider();
        versionClickCount = 0;
        if (sharedMediaLayout != null) sharedMediaLayout.onDestroy();
        if (menuHandler != null) menuHandler.deinitialize();

        // Menu
        menuHandler = new ProfileActivityMenus(context, resourceProvider, actionBar);

        // Shared media
        int initialTab = -1; // WIP
        sharedMediaLayout = new SharedMediaLayout(context, getDialogId(), sharedMediaPreloader, userInfo != null ? userInfo.common_chats_count : 0, sortMembers(), chatInfo, userInfo, initialTab, this, this, SharedMediaLayout.VIEW_TYPE_PROFILE_ACTIVITY, getResourceProvider()) {
            @Override
            protected boolean isSelf() {
                return isMyProfile;
            }
            @Override
            protected boolean isStoriesView() {
                return isMyProfile;
            }
            @Override
            protected void onSelectedTabChanged() {
                updateSelectedMediaTabText();
            }
            @Override
            protected void drawBackgroundWithBlur(Canvas canvas, float y, Rect rectTmp2, Paint backgroundPaint) {
                rootLayout.drawBlurRect(canvas, listView.getY() + getY() + y, rectTmp2, backgroundPaint, true);
            }
            @Override
            protected void invalidateBlur() {
                if (rootLayout != null) rootLayout.invalidateBlur();
            }
            @Override
            protected int getInitialTab() {
                return TAB_STORIES;
            }

            @Override
            protected boolean onMemberClick(TLRPC.ChatParticipant participant, boolean isLong, View view) {
                return handleMemberPress(participant, isLong, false, view);
            }

            @Override
            protected void showActionMode(boolean show) {
                super.showActionMode(show);
                if (isMyProfile) {
                    disableScroll(show);
                }
            }
        };
        sharedMediaLayout.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT));

        // sharedMediaLayout adds some action bar items, and order matters
        boolean qr = userId == getUserConfig().clientUserId && !isMyProfile;
        if (qr && ContactsController.getInstance(currentAccount).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE) == null) {
            ContactsController.getInstance(currentAccount).loadPrivacySettings();
        }
        menuHandler.initialize();
        menuHandler.setActionMode(false, sharedMediaLayout, null);
        menuHandler.setQrItemNeeded(qr, false);

        // Root view
        rootLayout = new ProfileActivityRootLayout(context, resourceProvider, actionBar) {
            @Override
            protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
                super.drawList(blurCanvas, top, views);
                if (listView == null || sharedMediaLayout == null) return;
                blurCanvas.save();
                blurCanvas.translate(0, listView.getY());
                sharedMediaLayout.drawListForBlur(blurCanvas, views);
                blurCanvas.restore();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // WIP: if (pinchToZoomHelper.isInOverlayMode()) return pinchToZoomHelper.onTouchEvent(ev);
                if (sharedMediaLayout.isInFastScroll() && sharedMediaLayout.isPinnedToTop()) {
                    return sharedMediaLayout.dispatchFastScrollEvent(ev);
                }
                if (sharedMediaLayout.checkPinchToZoom(ev)) {
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                checkMediaHeaderVisible();
            }
        };
        rootLayout.needBlur = true;

        // Coordinator
        ProfileCoordinatorLayout coordinator = new ProfileCoordinatorLayout(context);
        rootLayout.addView(coordinator);

        // Header
        headerView = new ProfileHeaderView(context, currentAccount, getDialogId(), isTopic(), rootLayout, actionBar, getResourceProvider());
        coordinator.addHeader(headerView);
        ProfileHeaderView.Avatar avatar = headerView.getAvatar();
        avatar.callback = new ProfileHeaderView.Avatar.Callback() {
            @Override
            public void onAvatarClick(@Nullable StoryViewer.PlaceProvider provider) {
                handleAvatarClick(provider);
            }

            @Override
            public boolean onAvatarLongClick() {
                return !isTopic() && uploadedAvatarBig == null && handleOpenAvatar();
            }
        };

        // List
        listView = new ProfileContentView(actionBar, sharedMediaLayout, getNotificationCenter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkMediaHeaderVisible();
            }
        });
        listView.setOnItemLongClickListener(this::handleListLongClick);
        listView.setOnItemClickListener(this::handleListClick);
        LinearLayoutManager listLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return uploader != null;
            }
        };
        listLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listLayoutManager.mIgnoreTopPadding = false;
        listView.setLayoutManager(listLayoutManager);
        coordinator.addContent(listView);
        ProfileContentAdapter listAdapter = new ProfileContentAdapter(this);
        listView.setAdapter(listAdapter);

        // Ban from group
        if (banGroupId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(banGroupId);
            if (chat != null) {
                if (banGroupParticipant == null) {
                    TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
                    req.channel = MessagesController.getInputChannel(chat);
                    req.participant = getMessagesController().getInputPeer(userId);
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response == null) return;
                        AndroidUtilities.runOnUIThread(() -> banGroupParticipant = ((TLRPC.TL_channels_channelParticipant) response).participant);
                    });
                }
                int height = rootLayout.addBanFromGroupView(() -> { handleBanFromGroup(chat); });
                listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), listView.getPaddingRight(), height);
                listView.setBottomGlowOffset(height);
            }
        }

        // Decorations
        rootLayout.blurBehindViews.add(sharedMediaLayout);
        rootLayout.blurredView.setOnClickListener(e -> finishPreviewFragment());
        rootLayout.addDecorationViews();
        updateBirthdayData();

        // Updates
        updateColors(false);
        updateListData("createView");
        updateProfileData(true);
        updateMenuData(false);
        updateSelectedMediaTabText();

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

        ThemeDescription.ThemeDescriptionDelegate themeDelegate = () -> {
            // WIP
            updateColors(true);
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

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        if (rootLayout != null) {
            rootLayout.getThemeDescriptions(arrayList);
        }
        if (listView != null) {
            listView.getThemeDescriptions(arrayList, themeDelegate);
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
                if ((mask & MessagesController.UPDATE_MASK_PHONE) != 0) {
                    if (listView != null) listView.notifyRowChange(Rows.InfoPhone);
                }
            } else if (chatId != 0) {
                boolean infoChanged = ((mask & MessagesController.UPDATE_MASK_CHAT) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0);
                if (infoChanged) updateProfileData(true);
                if ((mask & MessagesController.UPDATE_MASK_CHAT) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0) {
                    if ((mask & MessagesController.UPDATE_MASK_CHAT) != 0) {
                        updateOnlineData(false);
                        updateListData("NotificationCenter.updateInterfaces");
                    } else {
                        updateOnlineData(true);
                    }
                }
                if (infoChanged) {
                    if (listView != null) {
                        int count = listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = listView.getChildAt(a);
                            if (child instanceof UserCell) {
                                ((UserCell) child).update(mask);
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.chatOnlineCountDidLoad) {
            Long chatId = (Long) args[0];
            if (chatInfo == null || chat == null || chat.id != chatId) return;
            chatInfo.online_count = (Integer) args[1];
            updateOnlineData(true);
            updateProfileData(false);
        } else if (id == NotificationCenter.topicsDidLoaded) {
            if (topicId != 0) updateProfileData(false);
        } else if (id == NotificationCenter.reloadDialogPhotos) {
            updateProfileData(false);
        } else if (id == NotificationCenter.contactsDidLoad || id == NotificationCenter.channelRightsUpdated) {
            updateMenuData(true);
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull full = (TLRPC.ChatFull) args[0];
            if (full.id != chatId) return;
            boolean byChannelUsers = (Boolean) args[2];
            boolean loadChannelParticipants = chatInfo == null && full instanceof TLRPC.TL_channelFull;
            setChatInfo(full);
            if ((loadChannelParticipants || !byChannelUsers)) {
                updateChatMembersData(true);
            }
        } else if (id == NotificationCenter.groupCallUpdated) {
            Long chatId = (Long) args[0];
            if (chat != null && chatId == chat.id && ChatObject.canManageCalls(chat)) {
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chatId);
                setChatInfo(chatFull);
                updateChatMembersData(false);
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            final long uid = (Long) args[0];
            if (uid != userId) return;
            final TLRPC.UserFull full = (TLRPC.UserFull) args[1];
            setUserInfo(full);
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack(true);
        } else if (id == NotificationCenter.privacyRulesUpdated) {
            if (menuHandler == null) return;
            menuHandler.setQrItemNeeded(true, true);
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            updatePremiumData();
        } else if (id == NotificationCenter.userIsPremiumBlockedUpadted) {
            updatePremiumData();
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            boolean oldValue = isUserBlocked;
            isUserBlocked = getMessagesController().blockePeers.indexOfKey(userId) >= 0;
            if (oldValue != isUserBlocked) {
                updateMenuData(true);
                updateListData("NotificationCenter.blockedUsersDidLoad");
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
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) args[0];
            if (chatEncrypted != null && chat.id == chatEncrypted.id) {
                chatEncrypted = chat;
                updateListData("NotificationCenter.encryptedChatUpdated");
                if (flagSecure != null) {
                    flagSecure.invalidate();
                }
            }
        } else if (id == NotificationCenter.reloadInterface) {
            updateListData("NotificationCenter.reloadInterface");
        } else if (id == NotificationCenter.starBalanceUpdated) {
            updateListData("NotificationCenter.starBalanceUpdated");
        } else if (id == NotificationCenter.botStarsUpdated) {
            updateListData("NotificationCenter.botStarsUpdated");
        } else if (id == NotificationCenter.botStarsTransactionsLoaded) {
            updateListData("NotificationCenter.botStarsTransactionsLoaded");
        } else if (id == NotificationCenter.botInfoDidLoad) {
            final TL_bots.BotInfo info = (TL_bots.BotInfo) args[0];
            if (info.user_id == userId) {
                isBotInfoLoaded = true;
                updateListData("NotificationCenter.botInfoDidLoad");
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (listView == null) return;
            listView.invalidateViews();
        } else if (id == NotificationCenter.newSuggestionsAvailable) {
            if (listView == null) return;
            listView.notifyRowChange(Rows.SuggestionPassword);
            listView.notifyRowChange(Rows.SuggestionPhone);
            listView.notifyRowChange(Rows.SuggestionGrace);
        } else if (id == NotificationCenter.dialogDeleted) {
            final long dialogId = (long) args[0];
            if (getDialogId() == dialogId) {
                if (parentLayout != null && parentLayout.getLastFragment() == this) {
                    finishFragment();
                } else {
                    removeSelfFromStack();
                }
            }
        }
    }

    // CLICKS

    public void handleOpenBotApp() {
        TLRPC.User bot = getMessagesController().getUser(userId);
        getMessagesController().openApp(this, bot, null, getClassGuid(), null);
    }

    public void handleOpenUrl(String url, Browser.Progress progress) {
        if (url.startsWith("@")) {
            getMessagesController().openByUserName(url.substring(1), this, 0, progress);
        } else if (url.startsWith("#") || url.startsWith("$")) {
            DialogsActivity fragment = new DialogsActivity(null);
            fragment.setSearchString(url);
            presentFragment(fragment);
        } else if (url.startsWith("/")) {
            if (parentLayout.getFragmentStack().size() > 1) {
                BaseFragment previousFragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2);
                if (previousFragment instanceof ChatActivity) {
                    finishFragment();
                    ((ChatActivity) previousFragment).chatActivityEnterView.setCommand(null, url, false, false);
                }
            }
        }
    }

    public void handleUsernameSpanClick(TLRPC.TL_username usernameObj, Runnable onDone) {
        if (!usernameObj.editable) {
            TL_fragment.TL_getCollectibleInfo req = new TL_fragment.TL_getCollectibleInfo();
            TL_fragment.TL_inputCollectibleUsername input = new TL_fragment.TL_inputCollectibleUsername();
            input.username = usernameObj.username;
            req.collectible = input;
            int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TL_fragment.TL_collectibleInfo) {
                    TLObject obj;
                    if (userId != 0) {
                        obj = getMessagesController().getUser(userId);
                    } else {
                        obj = getMessagesController().getChat(chatId);
                    }
                    if (getContext() == null) {
                        return;
                    }
                    FragmentUsernameBottomSheet.open(getContext(), FragmentUsernameBottomSheet.TYPE_USERNAME, usernameObj.username, obj, (TL_fragment.TL_collectibleInfo) res, getResourceProvider());
                } else {
                    BulletinFactory.showError(err);
                }
                onDone.run();
            }));
            getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
        } else {
            String urlFinal = getMessagesController().linkPrefix + "/" +  usernameObj.username;
            if (chat == null || !chat.noforwards) {
                AndroidUtilities.addToClipboard(urlFinal);
                UndoView undoView = getUndoView();
                if (undoView != null) undoView.showWithAction(0, UndoView.ACTION_USERNAME_COPIED, null);
            }
            onDone.run();
        }
    }

    private void handleImageUpload() {
        if (uploader == null) return;
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        if (user == null) user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (user == null) return;
        // TextCell setAvatarCell = null;
        // RLottieImageView setAvatarImage = setAvatarCell != null ? setAvatarCell.getImageView() : null;
        uploader.openMenu(user.photo != null && user.photo.photo_big != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty), () -> {
            MessagesController.getInstance(currentAccount).deleteUserPhoto(null);
            // if (setAvatarImage != null) {
            //     setAvatarImage.getAnimatedDrawable().setCurrentFrame(0);
            // }
        }, dialog -> {
            if (!uploader.isUploadingImage()) {
                // if (setAvatarImage != null) {
                //     setAvatarImage.getAnimatedDrawable().setCustomEndFrame(86);
                //     setAvatarImage.playAnimation();
                // }
            } else {
                // if (setAvatarImage != null) {
                //     setAvatarImage.getAnimatedDrawable().setCurrentFrame(0, false);
                // }
            }
        }, 0);
        // if (setAvatarImage != null) {
        //     setAvatarImage.getAnimatedDrawable().setCurrentFrame(0);
        //     setAvatarImage.getAnimatedDrawable().setCustomEndFrame(43);
        //     setAvatarImage.playAnimation();
        // }
    }

    private void handleAddContact() {
        TLRPC.User user = getMessagesController().getUser(userId);
        Bundle args = new Bundle();
        args.putLong("user_id", user.id);
        args.putBoolean("addContact", true);
        args.putString("phone", PhoneFormat.stripExceptNumbers(arguments.getString("vcard_phone")));
        args.putString("first_name_card", arguments.getString("vcard_first_name"));
        args.putString("last_name_card", arguments.getString("vcard_last_name"));
        ContactAddActivity contactAddActivity = new ContactAddActivity(args, getResourceProvider());
        contactAddActivity.setDelegate(() -> {
            // WIP: Expand profile & update list
            if (getUndoView() != null) //noinspection deprecation
                getUndoView().showWithAction(dialogId, UndoView.ACTION_CONTACT_ADDED, user);
        });
        presentFragment(contactAddActivity);
    }

    public void handleSuggestionClick(int type, boolean yes) {
        if (yes) {
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().removeObserver(this, NotificationCenter.newSuggestionsAvailable);
                if (type == SettingsSuggestionCell.TYPE_GRACE) {
                    getMessagesController().removeSuggestion(0, "PREMIUM_GRACE");
                    Browser.openUrl(getContext(), getMessagesController().premiumManageSubscriptionUrl);
                } else {
                    getMessagesController().removeSuggestion(0, type == SettingsSuggestionCell.TYPE_PHONE ? "VALIDATE_PHONE_NUMBER" : "VALIDATE_PASSWORD");
                }
                getNotificationCenter().addObserver(this, NotificationCenter.newSuggestionsAvailable);
                updateListData("handleSuggestionClick");
            });
        } else {
            if (type == SettingsSuggestionCell.TYPE_PHONE) {
                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));
            } else {
                presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_VERIFY, null));
            }
        }
    }

    private void handleOpenLocation(boolean inMapsApp) {
        if (userInfo == null || userInfo.business_location == null) return;
        if (userInfo.business_location.geo_point != null && !inMapsApp) {
            LocationActivity fragment = new LocationActivity(3) {
                @Override
                protected boolean disablePermissionCheck() {
                    return true;
                }
            };
            fragment.setResourceProvider(getResourceProvider());
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.local_id = -1;
            message.peer_id = getMessagesController().getPeer(getDialogId());
            TLRPC.TL_messageMediaGeo media = new TLRPC.TL_messageMediaGeo();
            media.geo = userInfo.business_location.geo_point;
            media.address = userInfo.business_location.address;
            message.media = media;
            fragment.setSharingAllowed(false);
            fragment.setMessageObject(new MessageObject(UserConfig.selectedAccount, message, false, false));
            presentFragment(fragment);
        } else {
            String domain;
            if (BuildVars.isHuaweiStoreApp()) {
                domain = "mapapp://navigation";
            } else {
                domain = "http://maps.google.com/maps";
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, domain + "?q=" + userInfo.business_location.address )));
                getParentActivity().startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void handleSendGift() {
        if (userId == getUserConfig().getClientUserId()) {
            presentFragment(new PremiumPreviewFragment("my_profile_gift"));
            return;
        }
        if (UserObject.areGiftsDisabled(userInfo)) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(userId)))).show();
            return;
        }
        showDialog(new GiftSheet(getContext(), currentAccount, userId, null, null));
    }

    public void handleOpenQr() {
        Bundle args = new Bundle();
        args.putLong("chat_id", chatId);
        args.putLong("user_id", userId);
        presentFragment(new QrActivity(args));
    }

    private void handleEditProfileClick() {
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
    }

    private void handleActionBarMenuClick(int id) {
        if (getParentActivity() == null) return;
        if (id == -1) {
            finishFragment();
        } else if (id == AB_EDIT_INFO_ID) {
            presentFragment(new UserInfoActivity());
        } else if (id == AB_ADD_PHOTO_ID) {
            handleImageUpload();
        } else if (id == AB_EDIT_ID) {
            handleEditProfileClick();
        } else if (id == AB_QR_ID) {
            handleOpenQr();
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
            builder.setTitle(getString(R.string.DeleteContact));
            builder.setMessage(getString(R.string.AreYouSureDeleteContact));
            builder.setPositiveButton(getString(R.string.Delete), (dialogInterface, i) -> {
                ArrayList<TLRPC.User> list = new ArrayList<>(); list.add(user);
                getContactsController().deleteContact(list, true);
                user.contact = false;
                updateListData("ActionBar.AB_CONTACT_DELETE_ID");
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
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
            args.putString("selectAlertString", getString(R.string.SendContactToText));
            args.putString("selectAlertStringGroup", getString(R.string.SendContactToGroupText));
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
            handleUnblockUser();
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
                builder.setTitle(getString(R.string.BlockUser));
                builder.setMessage(AndroidUtilities.replaceTags(formatString("AreYouSureBlockContact2", R.string.AreYouSureBlockContact2, ContactsController.formatName(user.first_name, user.last_name))));
                builder.setPositiveButton(getString(R.string.BlockContact), (dialogInterface, i) -> {
                    getMessagesController().blockPeer(userId);
                    if (BulletinFactory.canShowBulletin(ProfileActivityReplacement.this)) {
                        BulletinFactory.createBanBulletin(ProfileActivityReplacement.this, true).show();
                    }
                });
                builder.setNegativeButton(getString(R.string.Cancel), null);
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) button.setTextColor(getThemedColor(Theme.key_text_RedBold));
            }
        } else if (id == AB_CONTACT_ADD_ID) {
            handleAddContact();
        } else if (id == AB_SHARE_ID) {
            TLRPC.User user = userId != 0 ? getMessagesController().getUser(userId) : null;
            TLRPC.Chat chat = chatId != 0 ? getMessagesController().getChat(chatId) : null;
            if (user == null && chat == null) return;
            try {
                String text = "";
                String username = user != null ? UserObject.getPublicUsername(user) : ChatObject.getPublicUsername(chat);
                String about = user != null ? (isBotInfoLoaded && userInfo != null ? userInfo.about : null) : (chatInfo != null ? chatInfo.about : null);
                if (TextUtils.isEmpty(about)) {
                    text = String.format("https://" + getMessagesController().linkPrefix + "/%s", username);
                } else {
                    text = String.format("%s https://" + getMessagesController().linkPrefix + "/%s", about, username);
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.BotShare)), 500);
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
            builder.setTitle(getString(R.string.AreYouSureSecretChatTitle));
            builder.setMessage(getString(R.string.AreYouSureSecretChat));
            builder.setPositiveButton(getString(R.string.Start), (dialogInterface, i) -> {
                if (MessagesController.getInstance(currentAccount).isFrozen()) {
                    AccountFrozenAlert.show(currentAccount);
                    return;
                }
                isCreatingEncryptedChat = true;
                getSecretChatHelper().startSecretChat(getParentActivity(), getMessagesController().getUser(userId));
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
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
            builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
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
            builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
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

    private boolean handleListClick(View view, int position, float x, float y) {
        if (listView == null || getParentActivity() == null) return false;
        ProfileContentAdapter adapter = (ProfileContentAdapter) listView.getAdapter();
        int kind = adapter == null ? -1 : adapter.getRows().kind(position);
        if (kind < 0) return false;
        listView.stopScroll();
        if (kind == Rows.Affiliate) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (userInfo != null && userInfo.starref_program != null) {
                final long selfId = getUserConfig().getClientUserId();
                BotStarsController.getInstance(currentAccount).getConnectedBot(getContext(), selfId, userId, connectedBot -> {
                    if (connectedBot == null) {
                        ChannelAffiliateProgramsFragment.showConnectAffiliateAlert(getContext(), currentAccount, userInfo.starref_program, getUserConfig().getClientUserId(), getResourceProvider(), false);
                    } else {
                        ChannelAffiliateProgramsFragment.showShareAffiliateAlert(getContext(), currentAccount, connectedBot, selfId, getResourceProvider());
                    }
                });
            } else if (user != null && user.bot_can_edit) {
                presentFragment(new AffiliateProgramFragment(userId));
            }
        } else if (kind == Rows.ActionsAddToContacts) {
            handleAddContact();
        } else if (kind == Rows.ActionsReportReaction) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(LocaleController.getString(R.string.ReportReaction));
            builder.setMessage(LocaleController.getString(R.string.ReportAlertReaction));

            TLRPC.Chat chat = getMessagesController().getChat(-reportReactionFromDialogId);
            CheckBoxCell[] cells = new CheckBoxCell[1];
            if (chat != null && ChatObject.canBlockUsers(chat)) {
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                cells[0] = new CheckBoxCell(getParentActivity(), 1, getResourceProvider());
                cells[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cells[0].setText(LocaleController.getString(R.string.BanUser), "", true, false);
                cells[0].setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                cells[0].setOnClickListener(v -> {
                    cells[0].setChecked(!cells[0].isChecked(), true);
                });
                builder.setView(linearLayout);
            }

            builder.setPositiveButton(LocaleController.getString(R.string.ReportChat), (dialog, which) -> {
                TLRPC.TL_messages_reportReaction req = new TLRPC.TL_messages_reportReaction();
                req.user_id = getMessagesController().getInputUser(userId);
                req.peer = getMessagesController().getInputPeer(reportReactionFromDialogId);
                req.id = reportReactionMessageId;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                });

                if (cells[0] != null && cells[0].isChecked()) {
                    TLRPC.User user = getMessagesController().getUser(userId);
                    getMessagesController().deleteParticipantFromChat(-reportReactionFromDialogId, user);
                }

                reportReactionMessageId = 0;
                updateListData("removeReportReaction");
                BulletinFactory.of(this).createReportSent(getResourceProvider()).show();
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
                dialog.dismiss();
            });
            AlertDialog dialog = builder.show();
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        } else if (kind == Rows.SecretSettingsKey) {
            Bundle args = new Bundle();
            args.putInt("chat_id", DialogObject.getEncryptedChatId(dialogId));
            presentFragment(new IdenticonActivity(args));
        } else if (kind == Rows.SecretSettingsTimer) {
            showDialog(AlertsCreator.createTTLAlert(getParentActivity(), chatEncrypted, getResourceProvider()).create());
        } else if (kind == Rows.Unblock) {
            handleUnblockUser();
        } else if (kind == Rows.ActionsAddToGroupButton) {
            final TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null) return false;
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO);
            args.putBoolean("resetDelegate", false);
            args.putBoolean("closeFragment", false);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, topicsFragment) -> {
                long did = dids.get(0).dialogId;

                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                if (chat != null && (chat.creator || chat.admin_rights != null && chat.admin_rights.add_admins)) {
                    getMessagesController().checkIsInChat(false, chat, user, (isInChatAlready, rightsAdmin, currentRank) -> AndroidUtilities.runOnUIThread(() -> {
                        ChatRightsEditActivity editRightsActivity = new ChatRightsEditActivity(userId, -did, rightsAdmin, null, null, currentRank, ChatRightsEditActivity.TYPE_ADD_BOT, true, !isInChatAlready, null);
                        editRightsActivity.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
                            @Override
                            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                                // WIP: disableProfileAnimation = true;
                                fragment.removeSelfFromStack();
                                getNotificationCenter().removeObserver(ProfileActivityReplacement.this, NotificationCenter.closeChats);
                                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                            }

                            @Override
                            public void didChangeOwner(TLRPC.User user) {
                            }
                        });
                        presentFragment(editRightsActivity);
                    }));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
                    builder.setTitle(LocaleController.getString(R.string.AddBot));
                    String chatName = chat == null ? "" : chat.title;
                    builder.setMessage(AndroidUtilities.replaceTags(formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, UserObject.getUserName(user), chatName)));
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    builder.setPositiveButton(LocaleController.getString(R.string.AddBot), (di, i) -> {
                        // WIP: disableProfileAnimation = true;

                        Bundle args1 = new Bundle();
                        args1.putBoolean("scrollToTopOnResume", true);
                        args1.putLong("chat_id", -did);
                        if (!getMessagesController().checkCanOpenChat(args1, fragment1)) {
                            return;
                        }
                        ChatActivity chatActivity = new ChatActivity(args1);
                        getNotificationCenter().removeObserver(ProfileActivityReplacement.this, NotificationCenter.closeChats);
                        getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                        getMessagesController().addUserToChat(-did, user, 0, null, chatActivity, true, null, null);
                        presentFragment(chatActivity, true);
                    });
                    showDialog(builder.create());
                }
                return true;
            });
            presentFragment(fragment);
        } else if (kind == Rows.Members) {
            List<TLRPC.ChatParticipant> participants = adapter.getRows().payload(Rows.Members);
            TLRPC.ChatParticipant participant = participants.get(position - adapter.getRows().position(Rows.Members));
            return handleMemberPress(participant, false, false, view);
        } else if (kind == Rows.MembersAdd) {
            handleAddMember();
        } else if (kind == Rows.InfoLocation) {
            if (chatInfo.location instanceof TLRPC.TL_channelLocation) {
                LocationActivity fragment = new LocationActivity(LocationActivity.LOCATION_TYPE_GROUP_VIEW);
                fragment.setChatLocation(chatId, (TLRPC.TL_channelLocation) chatInfo.location);
                presentFragment(fragment);
            }
        } else if (kind == Rows.ChannelOptionsSubscribers) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putInt("type", ChatUsersActivity.TYPE_USERS);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(chatInfo);
            presentFragment(fragment);
        } else if (kind == Rows.ChannelOptionsSubscribersRequests) {
            MemberRequestsActivity activity = new MemberRequestsActivity(chatId);
            presentFragment(activity);
        } else if (kind == Rows.ChannelOptionsAdministrators) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(chatInfo);
            presentFragment(fragment);
        } else if (kind == Rows.ChannelOptionsSettings) {
            handleEditProfileClick();
        } else if (kind == Rows.ActionsBotStarsBalance) {
            presentFragment(new BotStarsActivity(BotStarsActivity.TYPE_STARS, userId));
        } else if (kind == Rows.ActionsBotTonBalance) {
            presentFragment(new BotStarsActivity(BotStarsActivity.TYPE_TON, userId));
        } else if (kind == Rows.ChannelOptionsBalance) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putBoolean("start_from_monetization", true);
            presentFragment(new StatisticActivity(args));
        } else if (kind == Rows.ChannelOptionsBlockedUsers) {
            Bundle args = new Bundle();
            args.putLong("chat_id", chatId);
            args.putInt("type", ChatUsersActivity.TYPE_BANNED);
            ChatUsersActivity fragment = new ChatUsersActivity(args);
            fragment.setInfo(chatInfo);
            presentFragment(fragment);
        } else if (kind == Rows.MySettingsNotification) {
            presentFragment(new NotificationsSettingsActivity());
        } else if (kind == Rows.MySettingsPrivacy) {
            presentFragment(new PrivacySettingsActivity().setCurrentPassword(userPassword));
        } else if (kind == Rows.MySettingsData) {
            presentFragment(new DataSettingsActivity());
        } else if (kind == Rows.MySettingsChat) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        } else if (kind == Rows.MySettingsFilters) {
            presentFragment(new FiltersSetupActivity());
        } else if (kind == Rows.MySettingsLiteMode) {
            presentFragment(new LiteModeSettingsActivity());
        } else if (kind == Rows.MySettingsDevices) {
            presentFragment(new SessionsActivity(0));
        } else if (kind == Rows.HelpQuestion) {
            showDialog(AlertsCreator.createSupportAlert(this, getResourceProvider()));
        } else if (kind == Rows.HelpFaq) {
            Browser.openUrl(getParentActivity(), LocaleController.getString(R.string.TelegramFaqUrl));
        } else if (kind == Rows.HelpPolicy) {
            Browser.openUrl(getParentActivity(), LocaleController.getString(R.string.PrivacyPolicyUrl));
        } else if (kind == Rows.DebugSendLogs) {
            LogSender.sendLogs(getParentActivity(), false);
        } else if (kind == Rows.DebugSendLastLogs) {
            LogSender.sendLogs(getParentActivity(), true);
        } else if (kind == Rows.DebugClearLogs) {
            FileLog.cleanupLogs();
        } else if (kind == Rows.DebugSwitchBackend) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder1.setMessage(LocaleController.getString(R.string.AreYouSure));
            builder1.setTitle(LocaleController.getString(R.string.AppName));
            builder1.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
                SharedConfig.pushAuthKey = null;
                SharedConfig.pushAuthKeyId = null;
                SharedConfig.saveConfig();
                getConnectionsManager().switchBackend(true);
            });
            builder1.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder1.create());
        } else if (kind == Rows.MySettingsLanguage) {
            presentFragment(new LanguageSelectActivity());
        } else if (kind == Rows.MyUsername) {
            presentFragment(new ChangeUsernameActivity());
        } else if (kind == Rows.MyBio) {
            presentFragment(new UserInfoActivity());
        } else if (kind == Rows.MyPhoneNumber) {
            presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));
        } else if (kind == Rows.FeaturesPremium) {
            presentFragment(new PremiumPreviewFragment("settings"));
        } else if (kind == Rows.FeaturesStars) {
            presentFragment(new StarsIntroActivity());
        } else if (kind == Rows.FeaturesBusiness) {
            presentFragment(new PremiumPreviewFragment(PremiumPreviewFragment.FEATURES_BUSINESS, "settings"));
        } else if (kind == Rows.FeaturesGift) {
            UserSelectorBottomSheet.open(0, BirthdayController.getInstance(currentAccount).getState());
        } else if (kind == Rows.BotPermissionLocation) {
            BotLocation botLocation = adapter.getRows().payload(kind);
            if (botLocation != null) {
                botLocation.setGranted(!botLocation.granted(), () -> ((TextCell) view).setChecked(botLocation.granted()));
            }
        } else if (kind == Rows.BotPermissionBiometry) {
            BotBiometry botBiometry = adapter.getRows().payload(kind);
            if (botBiometry != null) {
                botBiometry.setGranted(!botBiometry.granted());
                ((TextCell) view).setChecked(botBiometry.granted());
            }
        } else if (kind == Rows.BotPermissionEmojiStatus) {
            ((TextCell) view).setChecked(!((TextCell) view).isChecked());
            if (botPermissionEmojiStatusReqId > 0) {
                getConnectionsManager().cancelRequest(botPermissionEmojiStatusReqId, true);
            }
            TL_bots.toggleUserEmojiStatusPermission req = new TL_bots.toggleUserEmojiStatusPermission();
            req.bot = getMessagesController().getInputUser(userId);
            req.enabled = ((TextCell) view).isChecked();
            if (userInfo != null) {
                userInfo.bot_can_manage_emoji_status = req.enabled;
            }
            final int[] reqId = new int[1];
            reqId[0] = botPermissionEmojiStatusReqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (!(res instanceof TLRPC.TL_boolTrue)) {
                    BulletinFactory.of(this).showForError(err);
                }
                if (botPermissionEmojiStatusReqId == reqId[0]) {
                    botPermissionEmojiStatusReqId = 0;
                }
            }));
        } else if (kind == Rows.InfoBizHours) {
            ProfileHoursCell cell = (ProfileHoursCell) view;
            TL_account.TL_businessWorkHours workHours = adapter.getRows().payload(kind);
            if (cell == null || workHours == null) return false;
            cell.set(workHours, !cell.isExpanded(),  cell.isShowInMyTimezone(), cell.hasDivider());
        } else if (kind == Rows.InfoBizLocation) {
            handleOpenLocation(false);
        } else if (kind == Rows.PersonalChannel) {
            if (userInfo == null) return false;
            Bundle args = new Bundle();
            args.putLong("chat_id", userInfo.personal_channel_id);
            presentFragment(new ChatActivity(args));
        } else if (kind == Rows.InfoBirthday) {
            if (birthdayEffect != null && birthdayEffect.start()) return true;
            if (handleListPressWithItemOptions(kind, view)) return true;
            TextDetailCell cell = (TextDetailCell) view;
            if (cell.hasImage()) handleSendGift();
        } else {
            return handleListPressWithPopup(kind, view, x, y);
        }
        return true;
    }

    private boolean handleListLongClick(View view, int position) {
        ProfileContentAdapter adapter = listView == null ? null : (ProfileContentAdapter) listView.getAdapter();
        int kind = adapter == null ? -1 : adapter.getRows().kind(position);
        if (kind < 0) return false;
        if (kind == Rows.MyVersion) {
            versionClickCount++;
            if (versionClickCount < 2 && !BuildVars.DEBUG_PRIVATE_VERSION) {
                try {
                    Toast.makeText(getParentActivity(), getString("DebugMenuLongPress", R.string.DebugMenuLongPress), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return true;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle(getString(R.string.DebugMenu));
            CharSequence[] items;
            items = new CharSequence[]{
                    getString(R.string.DebugMenuImportContacts),
                    getString(R.string.DebugMenuReloadContacts),
                    getString(R.string.DebugMenuResetContacts),
                    getString(R.string.DebugMenuResetDialogs),
                    BuildVars.DEBUG_VERSION ? null : (BuildVars.LOGS_ENABLED ? getString("DebugMenuDisableLogs", R.string.DebugMenuDisableLogs) : getString("DebugMenuEnableLogs", R.string.DebugMenuEnableLogs)),
                    SharedConfig.inappCamera ? getString("DebugMenuDisableCamera", R.string.DebugMenuDisableCamera) : getString("DebugMenuEnableCamera", R.string.DebugMenuEnableCamera),
                    getString("DebugMenuClearMediaCache", R.string.DebugMenuClearMediaCache),
                    getString(R.string.DebugMenuCallSettings),
                    null,
                    BuildVars.DEBUG_PRIVATE_VERSION || ApplicationLoader.isStandaloneBuild() || ApplicationLoader.isBetaBuild() ? getString("DebugMenuCheckAppUpdate", R.string.DebugMenuCheckAppUpdate) : null,
                    getString("DebugMenuReadAllDialogs", R.string.DebugMenuReadAllDialogs),
                    BuildVars.DEBUG_PRIVATE_VERSION ? (SharedConfig.disableVoiceAudioEffects ? "Enable voip audio effects" : "Disable voip audio effects") : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Clean app update" : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Reset suggestions" : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? getString(R.string.DebugMenuClearWebViewCache) : null,
                    getString(R.string.DebugMenuClearWebViewCookies),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? getString(SharedConfig.debugWebView ? R.string.DebugMenuDisableWebViewDebug : R.string.DebugMenuEnableWebViewDebug) : null,
                    (AndroidUtilities.isTabletInternal() && BuildVars.DEBUG_PRIVATE_VERSION) ? (SharedConfig.forceDisableTabletMode ? "Enable tablet mode" : "Disable tablet mode") : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? getString(SharedConfig.isFloatingDebugActive ? R.string.FloatingDebugDisable : R.string.FloatingDebugEnable) : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Force remove premium suggestions" : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Share device info" : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Force performance class" : null,
                    BuildVars.DEBUG_PRIVATE_VERSION && !InstantCameraView.allowBigSizeCameraDebug() ? (!SharedConfig.bigCameraForRound ? "Force big camera for round" : "Disable big camera for round") : null,
                    getString(DualCameraView.dualAvailableStatic(getContext()) ? "DebugMenuDualOff" : "DebugMenuDualOn"),
                    BuildVars.DEBUG_VERSION ? (SharedConfig.useSurfaceInStories ? "back to TextureView in stories" : "use SurfaceView in stories") : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? (SharedConfig.photoViewerBlur ? "do not blur in photoviewer" : "blur in photoviewer") : null,
                    !SharedConfig.payByInvoice ? "Enable Invoice Payment" : "Disable Invoice Payment",
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Update Attach Bots" : null,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? (!SharedConfig.isUsingCamera2(currentAccount) ? "Use Camera 2 API" : "Use old Camera 1 API") : null,
                    BuildVars.DEBUG_VERSION ? "Clear Mini Apps Permissions and Files" : null,
                    BuildVars.DEBUG_PRIVATE_VERSION ? "Clear all login tokens" : null,
                    SharedConfig.canBlurChat() && Build.VERSION.SDK_INT >= 31 ? (SharedConfig.useNewBlur ? "back to cpu blur" : "use new gpu blur") : null,
                    SharedConfig.adaptableColorInBrowser ? "Disabled adaptive browser colors" : "Enable adaptive browser colors",
                    SharedConfig.debugVideoQualities ? "Disable video qualities debug" : "Enable video qualities debug",
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? getString(SharedConfig.useSystemBoldFont ? R.string.DebugMenuDontUseSystemBoldFont : R.string.DebugMenuUseSystemBoldFont) : null,
                    "Reload app config",
                    !SharedConfig.forceForumTabs ? "Force Forum Tabs" : "Do Not Force Forum Tabs"
            };

            builder.setItems(items, (dialog, which) -> {
                if (which == 0) { // Import Contacts
                    getUserConfig().syncContacts = true;
                    getUserConfig().saveConfig(false);
                    getContactsController().forceImportContacts();
                } else if (which == 1) { // Reload Contacts
                    getContactsController().loadContacts(false, 0);
                } else if (which == 2) { // Reset Imported Contacts
                    getContactsController().resetImportedContacts();
                } else if (which == 3) { // Reset Dialogs
                    getMessagesController().forceResetDialogs();
                } else if (which == 4) { // Logs
                    BuildVars.LOGS_ENABLED = !BuildVars.LOGS_ENABLED;
                    SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
                    sharedPreferences.edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED).commit();
                    updateListData("logs");
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("app start time = " + ApplicationLoader.startTime);
                        try {
                            FileLog.d("buildVersion = " + ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionCode);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } else if (which == 5) { // In-app camera
                    SharedConfig.toggleInappCamera();
                } else if (which == 6) { // Clear sent media cache
                    getMessagesStorage().clearSentMedia();
                    SharedConfig.setNoSoundHintShowed(false);
                    SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                    editor.remove("archivehint").remove("proximityhint").remove("archivehint_l").remove("speedhint").remove("gifhint").remove("reminderhint").remove("soundHint").remove("themehint").remove("bganimationhint").remove("filterhint").remove("n_0").remove("storyprvhint").remove("storyhint").remove("storyhint2").remove("storydualhint").remove("storysvddualhint").remove("stories_camera").remove("dualcam").remove("dualmatrix").remove("dual_available").remove("archivehint").remove("askNotificationsAfter").remove("askNotificationsDuration").remove("viewoncehint").remove("voicepausehint").remove("taptostorysoundhint").remove("nothanos").remove("voiceoncehint").remove("savedhint").remove("savedsearchhint").remove("savedsearchtaghint").remove("groupEmojiPackHintShown").remove("newppsms").remove("monetizationadshint").remove("seekSpeedHintShowed").remove("unsupport_video/av01").remove("channelgifthint").remove("statusgiftpage").remove("multistorieshint").remove("channelsuggesthint").remove("trimvoicehint").apply();
                    MessagesController.getEmojiSettings(currentAccount).edit().remove("featured_hidden").remove("emoji_featured_hidden").commit();
                    SharedConfig.textSelectionHintShows = 0;
                    SharedConfig.lockRecordAudioVideoHint = 0;
                    SharedConfig.stickersReorderingHintUsed = false;
                    SharedConfig.forwardingOptionsHintShown = false;
                    SharedConfig.replyingOptionsHintShown = false;
                    SharedConfig.messageSeenHintCount = 3;
                    SharedConfig.emojiInteractionsHintCount = 3;
                    SharedConfig.dayNightThemeSwitchHintCount = 3;
                    SharedConfig.fastScrollHintCount = 3;
                    SharedConfig.stealthModeSendMessageConfirm = 2;
                    SharedConfig.updateStealthModeSendMessageConfirm(2);
                    SharedConfig.setStoriesReactionsLongPressHintUsed(false);
                    SharedConfig.setStoriesIntroShown(false);
                    SharedConfig.setMultipleReactionsPromoShowed(false);
                    ChatThemeController.getInstance(currentAccount).clearCache();
                    getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
                    RestrictedLanguagesSelectActivity.cleanup();
                    PersistColorPalette.getInstance(currentAccount).cleanup();
                    SharedPreferences prefs = getMessagesController().getMainSettings();
                    editor = prefs.edit();
                    editor.remove("peerColors").remove("profilePeerColors").remove("boostingappearance").remove("bizbothint").remove("movecaptionhint");
                    for (String key : prefs.getAll().keySet()) {
                        if (key.contains("show_gift_for_") || key.contains("bdayhint_") || key.contains("bdayanim_") || key.startsWith("ask_paid_message_") || key.startsWith("topicssidetabs")) {
                            editor.remove(key);
                        }
                    }
                    editor.apply();
                    editor = MessagesController.getNotificationsSettings(currentAccount).edit();
                    for (String key : MessagesController.getNotificationsSettings(currentAccount).getAll().keySet()) {
                        if (key.startsWith("dialog_bar_botver")) {
                            editor.remove(key);
                        }
                    }
                    editor.apply();
                } else if (which == 7) { // Call settings
                    VoIPHelper.showCallDebugSettings(getParentActivity());
                } else if (which == 8) { // ?
                    SharedConfig.toggleRoundCamera16to9();
                } else if (which == 9) { // Check app update
                    ((LaunchActivity) getParentActivity()).checkAppUpdate(true, null);
                } else if (which == 10) { // Read all chats
                    getMessagesStorage().readAllDialogs(-1);
                } else if (which == 11) { // Voip audio effects
                    SharedConfig.toggleDisableVoiceAudioEffects();
                } else if (which == 12) { // Clean app update
                    SharedConfig.pendingAppUpdate = null;
                    SharedConfig.saveConfig();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                } else if (which == 13) { // Reset suggestions
                    Set<String> suggestions = getMessagesController().pendingSuggestions;
                    suggestions.add("VALIDATE_PHONE_NUMBER");
                    suggestions.add("VALIDATE_PASSWORD");
                    getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
                } else if (which == 14) { // WebView Cache
                    ApplicationLoader.applicationContext.deleteDatabase("webview.db");
                    ApplicationLoader.applicationContext.deleteDatabase("webviewCache.db");
                    WebStorage.getInstance().deleteAllData();
                    try {
                        WebView webView = new WebView(ApplicationLoader.applicationContext);
                        webView.clearHistory();
                        webView.destroy();
                    } catch (Exception e) {}
                } else if (which == 15) {
                    CookieManager cookieManager = CookieManager.getInstance();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        cookieManager.removeAllCookies(null);
                        cookieManager.flush();
                    }
                } else if (which == 16) { // WebView debug
                    SharedConfig.toggleDebugWebView();
                    Toast.makeText(getParentActivity(), getString(SharedConfig.debugWebView ? R.string.DebugMenuWebViewDebugEnabled : R.string.DebugMenuWebViewDebugDisabled), Toast.LENGTH_SHORT).show();
                } else if (which == 17) { // Tablet mode
                    SharedConfig.toggleForceDisableTabletMode();

                    Activity activity = AndroidUtilities.findActivity(getContext());
                    final PackageManager pm = activity.getPackageManager();
                    final Intent intent = pm.getLaunchIntentForPackage(activity.getPackageName());
                    activity.finishAffinity(); // Finishes all activities.
                    activity.startActivity(intent);    // Start the launch activity
                    System.exit(0);
                } else if (which == 18) {
                    FloatingDebugController.setActive((LaunchActivity) getParentActivity(), !FloatingDebugController.isActive());
                } else if (which == 19) {
                    getMessagesController().loadAppConfig();
                    TLRPC.TL_help_dismissSuggestion req = new TLRPC.TL_help_dismissSuggestion();
                    req.suggestion = "VALIDATE_PHONE_NUMBER";
                    req.peer = new TLRPC.TL_inputPeerEmpty();
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        TLRPC.TL_help_dismissSuggestion req2 = new TLRPC.TL_help_dismissSuggestion();
                        req2.suggestion = "VALIDATE_PASSWORD";
                        req2.peer = new TLRPC.TL_inputPeerEmpty();
                        getConnectionsManager().sendRequest(req2, (res2, err2) -> {
                            getMessagesController().loadAppConfig();
                        });
                    });
                } else if (which == 20) {
                    int cpuCount = ConnectionsManager.CPU_COUNT;
                    int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
                    long minFreqSum = 0, minFreqCount = 0;
                    long maxFreqSum = 0, maxFreqCount = 0;
                    long curFreqSum = 0, curFreqCount = 0;
                    long capacitySum = 0, capacityCount = 0;
                    StringBuilder cpusInfo = new StringBuilder();
                    for (int i = 0; i < cpuCount; i++) {
                        Long minFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_min_freq");
                        Long curFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_cur_freq");
                        Long maxFreq = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
                        Long capacity = AndroidUtilities.getSysInfoLong("/sys/devices/system/cpu/cpu" + i + "/cpu_capacity");
                        cpusInfo.append("#").append(i).append(" ");
                        if (minFreq != null) {
                            cpusInfo.append("min=").append(minFreq / 1000L).append(" ");
                            minFreqSum += (minFreq / 1000L);
                            minFreqCount++;
                        }
                        if (curFreq != null) {
                            cpusInfo.append("cur=").append(curFreq / 1000L).append(" ");
                            curFreqSum += (curFreq / 1000L);
                            curFreqCount++;
                        }
                        if (maxFreq != null) {
                            cpusInfo.append("max=").append(maxFreq / 1000L).append(" ");
                            maxFreqSum += (maxFreq / 1000L);
                            maxFreqCount++;
                        }
                        if (capacity != null) {
                            cpusInfo.append("cpc=").append(capacity).append(" ");
                            capacitySum += capacity;
                            capacityCount++;
                        }
                        cpusInfo.append("\n");
                    }
                    StringBuilder info = new StringBuilder();
                    info.append(Build.MANUFACTURER).append(", ").append(Build.MODEL).append(" (").append(Build.PRODUCT).append(", ").append(Build.DEVICE).append(") ").append(" (android ").append(Build.VERSION.SDK_INT).append(")\n");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        info.append("SoC: ").append(Build.SOC_MANUFACTURER).append(", ").append(Build.SOC_MODEL).append("\n");
                    }
                    String gpuModel = AndroidUtilities.getSysInfoString("/sys/kernel/gpu/gpu_model");
                    if (gpuModel != null) {
                        info.append("GPU: ").append(gpuModel);
                        Long minClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_min_clock");
                        Long mminClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_mm_min_clock");
                        Long maxClock = AndroidUtilities.getSysInfoLong("/sys/kernel/gpu/gpu_max_clock");
                        if (minClock != null) {
                            info.append(", min=").append(minClock / 1000L);
                        }
                        if (mminClock != null) {
                            info.append(", mmin=").append(mminClock / 1000L);
                        }
                        if (maxClock != null) {
                            info.append(", max=").append(maxClock / 1000L);
                        }
                        info.append("\n");
                    }
                    ConfigurationInfo configurationInfo = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getDeviceConfigurationInfo();
                    info.append("GLES Version: ").append(configurationInfo.getGlEsVersion()).append("\n");
                    info.append("Memory: class=").append(AndroidUtilities.formatFileSize(memoryClass * 1024L * 1024L));
                    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                    ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
                    info.append(", total=").append(AndroidUtilities.formatFileSize(memoryInfo.totalMem));
                    info.append(", avail=").append(AndroidUtilities.formatFileSize(memoryInfo.availMem));
                    info.append(", low?=").append(memoryInfo.lowMemory);
                    info.append(" (threshold=").append(AndroidUtilities.formatFileSize(memoryInfo.threshold)).append(")");
                    info.append("\n");
                    info.append("Current class: ").append(SharedConfig.performanceClassName(SharedConfig.getDevicePerformanceClass())).append(", measured: ").append(SharedConfig.performanceClassName(SharedConfig.measureDevicePerformanceClass()));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        info.append(", suggest=").append(Build.VERSION.MEDIA_PERFORMANCE_CLASS);
                    }
                    info.append("\n");
                    info.append(cpuCount).append(" CPUs");
                    if (minFreqCount > 0) {
                        info.append(", avgMinFreq=").append(minFreqSum / minFreqCount);
                    }
                    if (curFreqCount > 0) {
                        info.append(", avgCurFreq=").append(curFreqSum / curFreqCount);
                    }
                    if (maxFreqCount > 0) {
                        info.append(", avgMaxFreq=").append(maxFreqSum / maxFreqCount);
                    }
                    if (capacityCount > 0) {
                        info.append(", avgCapacity=").append(capacitySum / capacityCount);
                    }
                    info.append("\n").append(cpusInfo);

                    LogSender.listCodecs("video/avc", info);
                    LogSender.listCodecs("video/hevc", info);
                    LogSender.listCodecs("video/x-vnd.on2.vp8", info);
                    LogSender.listCodecs("video/x-vnd.on2.vp9", info);

                    showDialog(new ShareAlert(getParentActivity(), null, info.toString(), false, null, false) {
                        @Override
                        protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                            if (!showToast) return;
                            AndroidUtilities.runOnUIThread(() -> {
                                BulletinFactory.createInviteSentBulletin(getParentActivity(), rootLayout, dids.size(), dids.size() == 1 ? dids.valueAt(0).id : 0, count, getThemedColor(Theme.key_undo_background), getThemedColor(Theme.key_undo_infoColor)).show();
                            }, 250);
                        }
                    });
                } else if (which == 21) {
                    AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
                    builder2.setTitle("Force performance class");
                    int currentClass = SharedConfig.getDevicePerformanceClass();
                    int trueClass = SharedConfig.measureDevicePerformanceClass();
                    builder2.setItems(new CharSequence[] {
                            AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? "**HIGH**" : "HIGH") + (trueClass == SharedConfig.PERFORMANCE_CLASS_HIGH ? " (measured)" : "")),
                            AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? "**AVERAGE**" : "AVERAGE") + (trueClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? " (measured)" : "")),
                            AndroidUtilities.replaceTags((currentClass == SharedConfig.PERFORMANCE_CLASS_LOW ? "**LOW**" : "LOW") + (trueClass == SharedConfig.PERFORMANCE_CLASS_LOW ? " (measured)" : ""))
                    }, (dialog2, which2) -> {
                        int newClass = 2 - which2;
                        if (newClass == trueClass) {
                            SharedConfig.overrideDevicePerformanceClass(-1);
                        } else {
                            SharedConfig.overrideDevicePerformanceClass(newClass);
                        }
                    });
                    builder2.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                    builder2.show();
                } else if (which == 22) {
                    SharedConfig.toggleRoundCamera();
                } else if (which == 23) {
                    boolean enabled = DualCameraView.dualAvailableStatic(getContext());
                    MessagesController.getGlobalMainSettings().edit().putBoolean("dual_available", !enabled).apply();
                    try {
                        Toast.makeText(getParentActivity(), getString(!enabled ? R.string.DebugMenuDualOnToast : R.string.DebugMenuDualOffToast), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {}
                } else if (which == 24) {
                    SharedConfig.toggleSurfaceInStories();
                    for (int i = 0; i < getParentLayout().getFragmentStack().size(); i++) {
                        getParentLayout().getFragmentStack().get(i).clearSheets();
                    }
                } else if (which == 25) {
                    SharedConfig.togglePhotoViewerBlur();
                } else if (which == 26) {
                    SharedConfig.togglePaymentByInvoice();
                } else if (which == 27) {
                    getMediaDataController().loadAttachMenuBots(false, true);
                } else if (which == 28) {
                    SharedConfig.toggleUseCamera2(currentAccount);
                } else if (which == 29) {
                    BotBiometry.clear();
                    BotLocation.clear();
                    BotDownloads.clear();
                    SetupEmojiStatusSheet.clear();
                } else if (which == 30) {
                    AuthTokensHelper.clearLogInTokens();
                } else if (which == 31) {
                    SharedConfig.toggleUseNewBlur();
                } else if (which == 32) {
                    SharedConfig.toggleBrowserAdaptableColors();
                } else if (which == 33) {
                    SharedConfig.toggleDebugVideoQualities();
                } else if (which == 34) {
                    SharedConfig.toggleUseSystemBoldFont();
                } else if (which == 35) {
                    MessagesController.getInstance(currentAccount).loadAppConfig(true);
                } else if (which == 36) {
                    SharedConfig.toggleForceForumTabs();
                }
            });
            builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
            return true;
        }
        else if (kind == Rows.Members) {
            List<TLRPC.ChatParticipant> participants = adapter.getRows().payload(Rows.Members);
            TLRPC.ChatParticipant participant = participants.get(position - adapter.getRows().position(Rows.Members));
            return handleMemberPress(participant, true, false, view);
        }
        else if (kind == Rows.InfoBirthday) {
            if (handleListPressWithItemOptions(kind, view)) return true;
            if (userInfo == null) return false;
            try {
                AndroidUtilities.addToClipboard(UserInfoActivity.birthdayString(userInfo.birthday));
                BulletinFactory.of(this).createCopyBulletin(getString(R.string.BirthdayCopied)).show();
            } catch (Exception e) {
                FileLog.e(e);
            }
            return true;
        }
        else {
            if (handleListPressWithItemOptions(kind, view)) return true;
            return handleListPressWithPopup(kind, view, view.getWidth() / 2f, (int) (view.getHeight() * .75f));
        }
    }
    
    // 'processOnClickOrPress()'
    private boolean handleListPressWithPopup(final int kind, final View view, final float x, final float y) {
        if (kind == Rows.InfoUsername || kind == Rows.MyUsername) {
            final String username;
            final TLRPC.TL_username usernameObj;
            if (userId != 0) {
                final TLRPC.User user = getMessagesController().getUser(userId);
                String username1 = UserObject.getPublicUsername(user);
                if (user == null || username1 == null) {
                    return false;
                }
                username = username1;
                usernameObj = DialogObject.findUsername(username, user);
            } else if (chatId != 0) {
                final TLRPC.Chat chat = getMessagesController().getChat(chatId);
                if (chat == null || topicId == 0 && !ChatObject.isPublic(chat)) {
                    return false;
                }
                username = ChatObject.getPublicUsername(chat);
                usernameObj = DialogObject.findUsername(username, chat);
            } else {
                return false;
            }
            if (userId == 0) {
                TLRPC.Chat chat = getMessagesController().getChat(chatId);
                String link;
                if (ChatObject.isPublic(chat)) {
                    link = "https://" + getMessagesController().linkPrefix + "/" + ChatObject.getPublicUsername(chat) + (topicId != 0 ? "/" + topicId : "");
                } else {
                    link = "https://" + getMessagesController().linkPrefix + "/c/" + chat.id + (topicId != 0 ? "/" + topicId : "");
                }
                ShareAlert shareAlert = new ShareAlert(getParentActivity(), null, link, false, link, false) {
                    @Override
                    protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                        if (!showToast) return;
                        AndroidUtilities.runOnUIThread(() -> {
                            BulletinFactory.createInviteSentBulletin(getParentActivity(), rootLayout, dids.size(), dids.size() == 1 ? dids.valueAt(0).id : 0, count, getThemedColor(Theme.key_undo_background), getThemedColor(Theme.key_undo_infoColor)).show();
                        }, 250);
                    }
                };
                showDialog(shareAlert);
                if (usernameObj != null && !usernameObj.editable) {
                    TL_fragment.TL_getCollectibleInfo req = new TL_fragment.TL_getCollectibleInfo();
                    TL_fragment.TL_inputCollectibleUsername input = new TL_fragment.TL_inputCollectibleUsername();
                    input.username = usernameObj.username;
                    req.collectible = input;
                    int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TL_fragment.TL_collectibleInfo) {
                            TL_fragment.TL_collectibleInfo info = (TL_fragment.TL_collectibleInfo) res;
                            TLObject obj;
                            if (userId != 0) {
                                obj = getMessagesController().getUser(userId);
                            } else {
                                obj = getMessagesController().getChat(chatId);
                            }
                            final String usernameStr = "@" + usernameObj.username;
                            final String date = LocaleController.getInstance().getFormatterBoostExpired().format(new Date(info.purchase_date * 1000L));
                            final String cryptoAmount = BillingController.getInstance().formatCurrency(info.crypto_amount, info.crypto_currency);
                            final String amount = BillingController.getInstance().formatCurrency(info.amount, info.currency);
                            BulletinFactory.of(shareAlert.bulletinContainer2, getResourceProvider())
                                    .createImageBulletin(
                                            R.drawable.filled_username,
                                            AndroidUtilities.withLearnMore(AndroidUtilities.replaceTags(formatString(R.string.FragmentChannelUsername, usernameStr, date, cryptoAmount, TextUtils.isEmpty(amount) ? "" : "("+amount+")")), () -> {
                                                Bulletin.hideVisible();
                                                Browser.openUrl(getContext(), info.url);
                                            })
                                    )
                                    .setOnClickListener(v -> {
                                        Bulletin.hideVisible();
                                        Browser.openUrl(getContext(), info.url);
                                    })
                                    .show(false);
                        } else {
                            BulletinFactory.showError(err);
                        }
                    }));
                    getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
                }
            } else {
                if (handleListPressWithItemOptions(kind, view)) return true;

                if (usernameObj != null && !usernameObj.editable) {
                    TL_fragment.TL_getCollectibleInfo req = new TL_fragment.TL_getCollectibleInfo();
                    TL_fragment.TL_inputCollectibleUsername input = new TL_fragment.TL_inputCollectibleUsername();
                    input.username = usernameObj.username;
                    req.collectible = input;
                    int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TL_fragment.TL_collectibleInfo) {
                            TLObject obj;
                            if (userId != 0) {
                                obj = getMessagesController().getUser(userId);
                            } else {
                                obj = getMessagesController().getChat(chatId);
                            }
                            FragmentUsernameBottomSheet.open(getContext(), FragmentUsernameBottomSheet.TYPE_USERNAME, usernameObj.username, obj, (TL_fragment.TL_collectibleInfo) res, getResourceProvider());
                        } else {
                            BulletinFactory.showError(err);
                        }
                    }));
                    getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
                    return true;
                }

                try {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    String text = "@" + username;
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.UsernameCopied), getResourceProvider()).show();
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", text);
                    clipboard.setPrimaryClip(clip);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return true;
        }
        else if (kind == Rows.InfoPhone || kind == Rows.MyPhoneNumber) {
            if (handleListPressWithItemOptions(kind, view)) return true;

            final TLRPC.User user = getMessagesController().getUser(userId);
            if (user == null || user.phone == null || user.phone.isEmpty() || getParentActivity() == null) {
                return false;
            }
            boolean isFragmentPhoneNumber = user.phone.matches("888\\d{8}");

            if (kind == Rows.InfoPhone && isFragmentPhoneNumber) {
                TL_fragment.TL_inputCollectiblePhone input = new TL_fragment.TL_inputCollectiblePhone();
                final String phone = input.phone = user.phone;
                TL_fragment.TL_getCollectibleInfo req = new TL_fragment.TL_getCollectibleInfo();
                req.collectible = input;
                int reqId = getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TL_fragment.TL_collectibleInfo) {
                        FragmentUsernameBottomSheet.open(getContext(), FragmentUsernameBottomSheet.TYPE_PHONE, phone, user, (TL_fragment.TL_collectibleInfo) res, getResourceProvider());
                    } else {
                        BulletinFactory.showError(err);
                    }
                }));
                getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
                return true;
            }

            ArrayList<CharSequence> items = new ArrayList<>();
            ArrayList<Integer> actions = new ArrayList<>();
            List<Integer> icons = new ArrayList<>();
            if (kind == Rows.InfoPhone) {
                if (userInfo != null && userInfo.phone_calls_available) {
                    icons.add(R.drawable.msg_calls);
                    items.add(LocaleController.getString(R.string.CallViaTelegram));
                    actions.add(PHONE_OPTION_TELEGRAM_CALL);
                    if (Build.VERSION.SDK_INT >= 18 && userInfo.video_calls_available) {
                        icons.add(R.drawable.msg_videocall);
                        items.add(LocaleController.getString(R.string.VideoCallViaTelegram));
                        actions.add(PHONE_OPTION_TELEGRAM_VIDEO_CALL);
                    }
                }
                icons.add(R.drawable.msg_calls_regular);
                items.add(LocaleController.getString(R.string.Call));
                actions.add(PHONE_OPTION_CALL);
            }
            icons.add(R.drawable.msg_copy);
            items.add(LocaleController.getString(R.string.Copy));
            actions.add(PHONE_OPTION_COPY);

            AtomicReference<ActionBarPopupWindow> popupWindowRef = new AtomicReference<>();
            RoundRectPopup popupLayout = new RoundRectPopup(getContext(), getResourceProvider());

            for (int i = 0; i < icons.size(); i++) {
                int action = actions.get(i);
                ActionBarMenuItem.addItem(popupLayout, icons.get(i), items.get(i), false, getResourceProvider()).setOnClickListener(v -> {
                    popupWindowRef.get().dismiss();
                    switch (action) {
                        case PHONE_OPTION_CALL:
                            try {
                                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + user.phone));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                getParentActivity().startActivityForResult(intent, 500);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            break;
                        case PHONE_OPTION_COPY:
                            try {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", "+" + user.phone);
                                clipboard.setPrimaryClip(clip);
                                if (AndroidUtilities.shouldShowClipboardToast()) {
                                    BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.PhoneCopied)).show();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            break;
                        case PHONE_OPTION_TELEGRAM_CALL:
                        case PHONE_OPTION_TELEGRAM_VIDEO_CALL:
                            if (getParentActivity() == null) {
                                return;
                            }
                            VoIPHelper.startCall(user, action == PHONE_OPTION_TELEGRAM_VIDEO_CALL, userInfo != null && userInfo.video_calls_available, getParentActivity(), userInfo, getAccountInstance());
                            break;
                    }
                });
            }
            if (isFragmentPhoneNumber) {
                FrameLayout gap = new FrameLayout(getContext());
                gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, getResourceProvider()));
                popupLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

                TextView fragmentInfoView = new TextView(getContext());
                fragmentInfoView.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
                fragmentInfoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                fragmentInfoView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, getResourceProvider()));
                fragmentInfoView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, getResourceProvider()));
                fragmentInfoView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector, getResourceProvider()), 0,6));

                SpannableStringBuilder spanned = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AnonymousNumberNotice)));

                int startIndex = TextUtils.indexOf(spanned, '*');
                int lastIndex = TextUtils.lastIndexOf(spanned, '*');
                if (startIndex != -1 && lastIndex != -1 && startIndex != lastIndex) {
                    spanned.replace(lastIndex, lastIndex + 1, "");
                    spanned.replace(startIndex, startIndex + 1, "");
                    spanned.setSpan(new TypefaceSpan(AndroidUtilities.bold()), startIndex, lastIndex - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spanned.setSpan(new ForegroundColorSpan(fragmentInfoView.getLinkTextColors().getDefaultColor()), startIndex, lastIndex - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                fragmentInfoView.setText(spanned);
                fragmentInfoView.setOnClickListener(v -> {
                    try {
                        v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://fragment.com")));
                    } catch (ActivityNotFoundException e) {
                        FileLog.e(e);
                    }
                });

                gap.setTag(R.id.fit_width_tag, 1);
                fragmentInfoView.setTag(R.id.fit_width_tag, 1);
                popupLayout.addView(fragmentInfoView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }

            popupWindowRef.set(popupLayout.show(this, rootLayout, view, x, y));
            return true;
        } else if (kind == Rows.InfoChatAbout || kind == Rows.InfoUserAbout || kind == Rows.InfoLocation || kind == Rows.MyBio) {
            if (kind == Rows.MyBio && (userInfo == null || TextUtils.isEmpty(userInfo.about))) {
                return false;
            }
            if (handleListPressWithItemOptions(kind, view)) return true;
            if (view instanceof AboutLinkCell && ((AboutLinkCell) view).onClick()) {
                return false;
            }
            String text;
            if (kind == Rows.InfoLocation) {
                text = chatInfo != null && chatInfo.location instanceof TLRPC.TL_channelLocation ? ((TLRPC.TL_channelLocation) chatInfo.location).address : null;
            } else if (kind == Rows.InfoChatAbout) {
                text = chatInfo != null ? chatInfo.about : null;
            } else {
                text = userInfo != null ? userInfo.about : null;
            }
            final String finalText = text;
            if (TextUtils.isEmpty(finalText)) {
                return false;
            }
            final String[] fromLanguage = new String[1];
            fromLanguage[0] = "und";
            final boolean translateButtonEnabled = MessagesController.getInstance(currentAccount).getTranslateController().isContextTranslateEnabled();
            final boolean[] withTranslate = new boolean[1];
            withTranslate[0] = kind != Rows.InfoLocation;
            final String toLang = LocaleController.getInstance().getCurrentLocale().getLanguage();
            Runnable showMenu = () -> {
                if (getParentActivity() == null) {
                    return;
                }
                CharSequence[] items = withTranslate[0] ? new CharSequence[]{LocaleController.getString(R.string.Copy), LocaleController.getString(R.string.TranslateMessage)} : new CharSequence[]{LocaleController.getString(R.string.Copy)};
                int[] icons = withTranslate[0] ? new int[] {R.drawable.msg_copy, R.drawable.msg_translate} : new int[] {R.drawable.msg_copy};

                AtomicReference<ActionBarPopupWindow> popupWindowRef = new AtomicReference<>();
                RoundRectPopup popupLayout = new RoundRectPopup(getContext(), getResourceProvider());

                for (int i = 0; i < icons.length; i++) {
                    int j = i;
                    ActionBarMenuItem.addItem(popupLayout, icons[i], items[i], false, getResourceProvider()).setOnClickListener(v -> {
                        popupWindowRef.get().dismiss();
                        try {
                            if (j == 0) {
                                AndroidUtilities.addToClipboard(finalText);
                                if (kind == Rows.MyBio) {
                                    BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.BioCopied)).show();
                                } else {
                                    BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                                }
                            } else if (j == 1) {
                                TranslateAlert2.showAlert(fragmentView.getContext(), this, currentAccount, fromLanguage[0], toLang, finalText, null, false, span -> {
                                    if (span != null) {
                                        handleOpenUrl(span.getURL(), null);
                                        return true;
                                    }
                                    return false;
                                }, null);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }

                popupWindowRef.set(popupLayout.show(this, rootLayout, view, x, y));
            };
            if (withTranslate[0]) {
                if (LanguageDetector.hasSupport()) {
                    LanguageDetector.detectLanguage(finalText, (fromLang) -> {
                        fromLanguage[0] = fromLang;
                        withTranslate[0] = fromLang != null && (!fromLang.equals(toLang) || fromLang.equals("und")) && (
                                translateButtonEnabled && !RestrictedLanguagesSelectActivity.getRestrictedLanguages().contains(fromLang) ||
                                        (chat != null && (chat.has_link || ChatObject.isPublic(chat))) && ("uk".equals(fromLang) || "ru".equals(fromLang)));
                        showMenu.run();
                    }, (error) -> {
                        FileLog.e("mlkit: failed to detect language in selection", error);
                        showMenu.run();
                    });
                } else {
                    showMenu.run();
                }
            } else {
                showMenu.run();
            }
            return true;
        } else if (kind == Rows.InfoBizHours || kind == Rows.InfoBizLocation) {
            if (getParentActivity() == null || userInfo == null) {
                return false;
            }
            final String finalText;
            if (kind == Rows.InfoBizHours) {
                if (userInfo.business_work_hours == null) return false;
                finalText = OpeningHoursActivity.toString(currentAccount, userInfo.user, userInfo.business_work_hours);
            } else {
                if (handleListPressWithItemOptions(kind, view)) return true;
                if (userInfo.business_location == null) return false;
                finalText = userInfo.business_location.address;
            }

            AtomicReference<ActionBarPopupWindow> popupWindowRef = new AtomicReference<>();
            RoundRectPopup popupLayout = new RoundRectPopup(getContext(), getResourceProvider());

            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_copy, LocaleController.getString(R.string.Copy), false, getResourceProvider()).setOnClickListener(v -> {
                popupWindowRef.get().dismiss();
                try {
                    AndroidUtilities.addToClipboard(finalText);
                    if (kind == Rows.InfoBizHours) {
                        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.BusinessHoursCopied)).show();
                    } else {
                        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.BusinessLocationCopied)).show();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });

            popupWindowRef.set(popupLayout.show(this, rootLayout, view, x, y));
            return true;
        }
        return false;
    }

    // 'editRow()'
    private boolean handleListPressWithItemOptions(int kind, View view) {
        if (!isMyProfile) return false;
        if (view instanceof ProfileChannelCell) {
            view = ((ProfileChannelCell) view).dialogCell;
        }
        TLRPC.User user = getUserConfig().getCurrentUser();
        if (user == null) return false;
        TLRPC.UserFull userFull = userInfo == null ? getMessagesController().getUserFull(user.id) : userInfo;
        if (userFull == null) return false;

        String copyButton = getString(R.string.Copy);
        String textToCopy = null;
        if (kind == Rows.InfoChatAbout || kind == Rows.InfoUserAbout || kind == Rows.MyBio) {
            textToCopy = userFull.about;
        } else if (kind == Rows.InfoBizHours) {
            textToCopy = OpeningHoursActivity.toString(currentAccount, user, userFull.business_work_hours);
            copyButton = getString(R.string.ProfileHoursCopy);
        } else if (kind == Rows.InfoBizLocation) {
            textToCopy = userFull.business_location.address;
            copyButton = getString(R.string.ProfileLocationCopy);
        } else if (kind == Rows.InfoUsername) {
            textToCopy = UserObject.getPublicUsername(user);
            if (textToCopy != null) textToCopy = "@" + textToCopy;
            copyButton = getString(R.string.ProfileCopyUsername);
        } else if (kind == Rows.InfoPhone) {
            textToCopy = user.phone;
        } else if (kind == Rows.InfoBirthday) {
            textToCopy = UserInfoActivity.birthdayString(userInfo.birthday);
        }

        ItemOptions itemOptions = ItemOptions.makeOptions(rootLayout, getResourceProvider(), view);
        itemOptions.setGravity(Gravity.LEFT);

        if (kind == Rows.InfoBizLocation && userFull.business_location != null) {
            if (userFull.business_location.geo_point != null) {
                itemOptions.add(R.drawable.msg_view_file, getString(R.string.ProfileLocationView), () -> handleOpenLocation(false));
            }
            itemOptions.add(R.drawable.msg_map, getString(R.string.ProfileLocationMaps), () -> handleOpenLocation(true));
        }

        if (textToCopy != null) {
            final String text = textToCopy;
            itemOptions.add(R.drawable.msg_copy, copyButton, () -> AndroidUtilities.addToClipboard(text));
        }

        if (kind == Rows.InfoBizHours) {
            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileHoursEdit), () -> {
                presentFragment(new OpeningHoursActivity());
            });
            itemOptions.add(R.drawable.msg_delete, getString(R.string.ProfileHoursRemove), true, () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.BusinessHoursClearTitle));
                builder.setMessage(LocaleController.getString(R.string.BusinessHoursClearMessage));
                builder.setPositiveButton(LocaleController.getString(R.string.Remove), (di, w) -> {
                    TL_account.updateBusinessWorkHours req = new TL_account.updateBusinessWorkHours();
                    TLRPC.UserFull currUserFull = ProfileActivityReplacement.this.userInfo;
                    if (currUserFull != null) {
                        currUserFull.business_work_hours = null;
                        currUserFull.flags2 &=~ 1;
                    }
                    getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (err != null) {
                            BulletinFactory.showError(err);
                        } else if (res instanceof TLRPC.TL_boolFalse) {
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                        }
                    }));
                    if (listView != null) listView.updateRows(old -> old.copy((next, k) -> k != Rows.InfoBizHours));
                    getMessagesStorage().updateUserInfo(currUserFull, false);
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
            });
        } else if (kind == Rows.InfoBizLocation) {
            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileLocationEdit), () -> {
                presentFragment(new org.telegram.ui.Business.LocationActivity());
            });
            itemOptions.add(R.drawable.msg_delete, getString(R.string.ProfileLocationRemove), true, () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.BusinessLocationClearTitle));
                builder.setMessage(LocaleController.getString(R.string.BusinessLocationClearMessage));
                builder.setPositiveButton(LocaleController.getString(R.string.Remove), (di, w) -> {
                    TL_account.updateBusinessLocation req = new TL_account.updateBusinessLocation();
                    TLRPC.UserFull currUserFull = ProfileActivityReplacement.this.userInfo;
                    if (currUserFull != null) {
                        currUserFull.business_location = null;
                        currUserFull.flags2 &=~ 2;
                    }
                    getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (err != null) {
                            BulletinFactory.showError(err);
                        } else if (res instanceof TLRPC.TL_boolFalse) {
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                        }
                    }));
                    if (listView != null) listView.updateRows(old -> old.copy((next, k) -> k != Rows.InfoBizLocation));
                    getMessagesStorage().updateUserInfo(currUserFull, false);
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
            });
        } else if (kind == Rows.InfoUsername) {
            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileUsernameEdit), () -> {
                presentFragment(new ChangeUsernameActivity());
            });
        } else if (kind == Rows.InfoChatAbout || kind == Rows.InfoUserAbout || kind == Rows.MyBio) {
            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileEditBio), () -> {
                presentFragment(new UserInfoActivity());
            });
        } else if (kind == Rows.InfoPhone) {
            itemOptions.add(R.drawable.menu_storage_path, getString(R.string.ProfilePhoneEdit), () -> {
                presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER));
            });
        } else if (kind == Rows.InfoBirthday) {
            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileBirthdayChange), () -> {
                showDialog(AlertsCreator.createBirthdayPickerDialog(getContext(), getString(R.string.EditProfileBirthdayTitle), getString(R.string.EditProfileBirthdayButton), userFull.birthday, birthday -> {
                    TL_account.updateBirthday req = new TL_account.updateBirthday();
                    req.flags |= 1;
                    req.birthday = birthday;
                    TLRPC.UserFull currUserFull = ProfileActivityReplacement.this.userInfo;
                    TL_account.TL_birthday oldBirthday = currUserFull != null ? currUserFull.birthday : null;
                    if (currUserFull != null) {
                        currUserFull.flags2 |= 32;
                        currUserFull.birthday = birthday;
                    }
                    getMessagesController().invalidateContentSettings();
                    getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TLRPC.TL_boolTrue) {
                            BulletinFactory.of(ProfileActivityReplacement.this)
                                    .createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.PrivacyBirthdaySetDone))
                                    .setDuration(Bulletin.DURATION_PROLONG).show();
                        } else {
                            if (currUserFull != null) {
                                if (oldBirthday == null) {
                                    currUserFull.flags2 &=~ 32;
                                } else {
                                    currUserFull.flags2 |= 32;
                                }
                                currUserFull.birthday = oldBirthday;
                                getMessagesStorage().updateUserInfo(currUserFull, false);
                            }
                            if (err != null && err.text != null && err.text.startsWith("FLOOD_WAIT_")) {
                                if (getContext() != null) {
                                    showDialog(
                                            new AlertDialog.Builder(getContext(), resourceProvider)
                                                    .setTitle(getString(R.string.PrivacyBirthdayTooOftenTitle))
                                                    .setMessage(getString(R.string.PrivacyBirthdayTooOftenMessage))
                                                    .setPositiveButton(getString(R.string.OK), null)
                                                    .create()
                                    );
                                }
                            } else {
                                BulletinFactory.of(ProfileActivityReplacement.this)
                                        .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError))
                                        .show();
                            }
                        }
                    }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);
                }, () -> {
                    BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                    params.transitionFromLeft = true;
                    params.allowNestedScroll = false;
                    showAsSheet(new PrivacyControlActivity(PrivacyControlActivity.PRIVACY_RULES_TYPE_BIRTHDAY), params);
                }, getResourceProvider()).create());
            });
            itemOptions.add(R.drawable.msg_delete, getString(R.string.Remove), true, () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.BirthdayClearTitle));
                builder.setMessage(LocaleController.getString(R.string.BirthdayClearMessage));
                builder.setPositiveButton(LocaleController.getString(R.string.Remove), (di, w) -> {
                    TL_account.updateBirthday req = new TL_account.updateBirthday();
                    TLRPC.UserFull currUserFull = ProfileActivityReplacement.this.userInfo;
                    if (currUserFull != null) {
                        currUserFull.birthday = null;
                        currUserFull.flags2 &=~ 32;
                    }
                    getMessagesController().invalidateContentSettings();
                    getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (err != null) {
                            BulletinFactory.showError(err);
                        } else if (res instanceof TLRPC.TL_boolFalse) {
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                        }
                    }));
                    if (listView != null) listView.updateRows(old -> old.copy((next, k) -> k != Rows.InfoBirthday));
                    getMessagesStorage().updateUserInfo(currUserFull, false);
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
            });
        } else if (kind == Rows.PersonalChannel) {
            TLRPC.Chat channel = getMessagesController().getChat(userFull.personal_channel_id);
            if (channel != null && ChatObject.getPublicUsername(channel) != null) {
                itemOptions.add(R.drawable.msg_copy, getString(R.string.ProfileChannelCopy), () -> {
                    AndroidUtilities.addToClipboard("https://" + getMessagesController().linkPrefix + "/" + ChatObject.getPublicUsername(channel));
                });
            }
            itemOptions.add(R.drawable.msg_edit, getString(R.string.ProfileChannelChange), () -> {
                presentFragment(new UserInfoActivity());
            });
            itemOptions.add(R.drawable.msg_delete, getString(R.string.Remove), true, () -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.ProfileChannelClearTitle));
                builder.setMessage(LocaleController.getString(R.string.ProfileChannelClearMessage));
                builder.setPositiveButton(LocaleController.getString(R.string.Remove), (di, w) -> {
                    TL_account.updatePersonalChannel req = new TL_account.updatePersonalChannel();
                    req.channel = new TLRPC.TL_inputChannelEmpty();
                    TLRPC.UserFull currUserFull = ProfileActivityReplacement.this.userInfo;
                    if (currUserFull != null) {
                        currUserFull.personal_channel_id = 0;
                        currUserFull.personal_channel_message = 0;
                        currUserFull.flags2 &=~ 64;
                    }
                    getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (err != null) {
                            BulletinFactory.showError(err);
                        } else if (res instanceof TLRPC.TL_boolFalse) {
                            BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                        }
                    }));
                    updateListData("personal_channel_removed");
                    getMessagesStorage().updateUserInfo(currUserFull, false);
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
            });
        }

        if (itemOptions.getItemsCount() <= 0) {
            return false;
        }
        itemOptions.show();
        return true;
    }

    private void handleUnblockUser() {
        getMessagesController().unblockPeer(userId);
        if (BulletinFactory.canShowBulletin(this)) {
            BulletinFactory.createBanBulletin(this, false).show();
        }
    }

    private void handleAddMember() {
        if (chat == null) return;
        Bundle args = new Bundle();
        args.putBoolean("addToGroup", true);
        args.putLong("chatId", chat.id);
        GroupCreateActivity fragment = new GroupCreateActivity(args);
        fragment.setInfo(chatInfo);
        if (chatInfo != null && chatInfo.participants != null) {
            LongSparseArray<TLObject> users = new LongSparseArray<>();
            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                users.put(chatInfo.participants.participants.get(a).user_id, null);
            }
            fragment.setIgnoreUsers(users);
        }
        fragment.setDelegate2((users, fwdCount) -> {
            HashSet<Long> currentParticipants = new HashSet<>();
            ArrayList<TLRPC.User> addedUsers = new ArrayList<>();
            if (chatInfo != null && chatInfo.participants != null && chatInfo.participants.participants != null) {
                for (int i = 0; i < chatInfo.participants.participants.size(); i++) {
                    currentParticipants.add(chatInfo.participants.participants.get(i).user_id);
                }
            }
            getMessagesController().addUsersToChat(chat, this, users, fwdCount, addedUsers::add, restrictedUser -> {
                for (int i = 0; i < chatInfo.participants.participants.size(); i++) {
                    if (chatInfo.participants.participants.get(i).user_id == restrictedUser.id) {
                        chatInfo.participants.participants.remove(i);
                        updateChatMembersData(true);
                        break;
                    }
                }
            }, () -> {
                int N = addedUsers.size();
                for (int a = 0; a < N; a++) {
                    TLRPC.User user = addedUsers.get(a);
                    if (!currentParticipants.contains(user.id)) {
                        if (chatInfo.participants == null) {
                            chatInfo.participants = new TLRPC.TL_chatParticipants();
                        }
                        if (ChatObject.isChannel(chat)) {
                            TLRPC.TL_chatChannelParticipant channelParticipant1 = new TLRPC.TL_chatChannelParticipant();
                            channelParticipant1.channelParticipant = new TLRPC.TL_channelParticipant();
                            channelParticipant1.channelParticipant.inviter_id = getUserConfig().getClientUserId();
                            channelParticipant1.channelParticipant.peer = new TLRPC.TL_peerUser();
                            channelParticipant1.channelParticipant.peer.user_id = user.id;
                            channelParticipant1.channelParticipant.date = getConnectionsManager().getCurrentTime();
                            channelParticipant1.user_id = user.id;
                            chatInfo.participants.participants.add(channelParticipant1);
                        } else {
                            TLRPC.ChatParticipant participant = new TLRPC.TL_chatParticipant();
                            participant.user_id = user.id;
                            participant.inviter_id = getAccountInstance().getUserConfig().clientUserId;
                            chatInfo.participants.participants.add(participant);
                        }
                        chatInfo.participants_count++;
                        getMessagesController().putUser(user, false);
                    }
                }
                updateChatMembersData(true);
            });

        });
        presentFragment(fragment);
    }


    private void handleEditRights(int action, TLRPC.User user, TLRPC.ChatParticipant participant, TLRPC.TL_chatAdminRights adminRights, TLRPC.TL_chatBannedRights bannedRights, String rank, boolean editingAdmin) {
        if (chat == null) return;
        boolean[] needShowBulletin = new boolean[1];
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(user.id, chatId, adminRights, chat.default_banned_rights, bannedRights, rank, action, true, false, null) {
            @Override
            public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                if (!isOpen && backward && needShowBulletin[0] && BulletinFactory.canShowBulletin(this)) {
                    BulletinFactory.createPromoteToAdminBulletin(this, user.first_name).show();
                }
            }
        };
        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
            @Override
            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                if (action == 0) {
                    if (participant instanceof TLRPC.TL_chatChannelParticipant) {
                        TLRPC.TL_chatChannelParticipant channelParticipant1 = ((TLRPC.TL_chatChannelParticipant) participant);
                        if (rights == 1) {
                            channelParticipant1.channelParticipant = new TLRPC.TL_channelParticipantAdmin();
                            channelParticipant1.channelParticipant.flags |= 4;
                        } else {
                            channelParticipant1.channelParticipant = new TLRPC.TL_channelParticipant();
                        }
                        channelParticipant1.channelParticipant.inviter_id = getUserConfig().getClientUserId();
                        channelParticipant1.channelParticipant.peer = new TLRPC.TL_peerUser();
                        channelParticipant1.channelParticipant.peer.user_id = participant.user_id;
                        channelParticipant1.channelParticipant.date = participant.date;
                        channelParticipant1.channelParticipant.banned_rights = rightsBanned;
                        channelParticipant1.channelParticipant.admin_rights = rightsAdmin;
                        channelParticipant1.channelParticipant.rank = rank;
                    } else if (participant != null) {
                        TLRPC.ChatParticipant newParticipant;
                        if (rights == 1) {
                            newParticipant = new TLRPC.TL_chatParticipantAdmin();
                        } else {
                            newParticipant = new TLRPC.TL_chatParticipant();
                        }
                        newParticipant.user_id = participant.user_id;
                        newParticipant.date = participant.date;
                        newParticipant.inviter_id = participant.inviter_id;
                        int index = chatInfo.participants.participants.indexOf(participant);
                        if (index >= 0) {
                            chatInfo.participants.participants.set(index, newParticipant);
                        }
                    }
                    if (rights == 1 && !editingAdmin) {
                        needShowBulletin[0] = true;
                    }
                } else if (action == 1) {
                    if (rights == 0) {
                        if (chat.megagroup && chatInfo != null && chatInfo.participants != null) {
                            boolean changed = false;
                            for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                                TLRPC.ChannelParticipant p = ((TLRPC.TL_chatChannelParticipant) chatInfo.participants.participants.get(a)).channelParticipant;
                                if (MessageObject.getPeerId(p.peer) == participant.user_id) {
                                    chatInfo.participants_count--;
                                    chatInfo.participants.participants.remove(a);
                                    changed = true;
                                    break;
                                }
                            }
                            if (chatInfo != null && chatInfo.participants != null) {
                                for (int a = 0; a < chatInfo.participants.participants.size(); a++) {
                                    TLRPC.ChatParticipant p = chatInfo.participants.participants.get(a);
                                    if (p.user_id == participant.user_id) {
                                        chatInfo.participants.participants.remove(a);
                                        changed = true;
                                        break;
                                    }
                                }
                            }
                            if (changed) {
                                updateChatMembersData(true);
                            }
                        }
                    }
                }
            }

            @Override
            public void didChangeOwner(TLRPC.User user) {
                UndoView undoView = getUndoView();
                undoView.showWithAction(-chatId, chat.megagroup ? UndoView.ACTION_OWNER_TRANSFERED_GROUP : UndoView.ACTION_OWNER_TRANSFERED_CHANNEL, user);
            }
        });
        presentFragment(fragment);
    }

    private boolean handleMemberPress(TLRPC.ChatParticipant participant, boolean isLong, boolean resultOnly, View view) {
        if (getParentActivity() == null) return false;
        if (!isLong) {
            if (participant.user_id == getUserConfig().getClientUserId()) return false;
            Bundle args = new Bundle();
            args.putLong("user_id", participant.user_id);
            args.putBoolean("preload_messages", true);
            presentFragment(new ProfileActivityReplacement(args));
            return true;
        }
        TLRPC.User user = getMessagesController().getUser(participant.user_id);
        if (user == null || participant.user_id == getUserConfig().getClientUserId()) {
            return false;
        }
        long selectedUser = participant.user_id;
        boolean allowKick;
        boolean canEditAdmin;
        boolean canRestrict;
        boolean editingAdmin;
        final TLRPC.ChannelParticipant channelParticipant;

        if (ChatObject.isChannel(chat)) {
            channelParticipant = ((TLRPC.TL_chatChannelParticipant) participant).channelParticipant;
            TLRPC.User u = getMessagesController().getUser(participant.user_id);
            canEditAdmin = ChatObject.canAddAdmins(chat);
            if (canEditAdmin && (channelParticipant instanceof TLRPC.TL_channelParticipantCreator || channelParticipant instanceof TLRPC.TL_channelParticipantAdmin && !channelParticipant.can_edit)) {
                canEditAdmin = false;
            }
            allowKick = canRestrict = ChatObject.canBlockUsers(chat) && (!(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit);
            if (chat.gigagroup) {
                canRestrict = false;
            }
            editingAdmin = channelParticipant instanceof TLRPC.TL_channelParticipantAdmin;
        } else {
            channelParticipant = null;
            allowKick = chat.creator || participant instanceof TLRPC.TL_chatParticipant && (ChatObject.canBlockUsers(chat) || participant.inviter_id == getUserConfig().getClientUserId());
            canEditAdmin = chat.creator;
            canRestrict = chat.creator;
            editingAdmin = participant instanceof TLRPC.TL_chatParticipantAdmin;
        }

        boolean result = (canEditAdmin || canRestrict || allowKick);
        if (resultOnly || !result) {
            return result;
        }

        Utilities.Callback<Integer> openRightsEdit = action -> {
            if (channelParticipant != null) {
                handleEditRights(action, user, participant, channelParticipant.admin_rights, channelParticipant.banned_rights, channelParticipant.rank, editingAdmin);
            } else {
                handleEditRights(action, user, participant, null, null, "", editingAdmin);
            }
        };

        ItemOptions.makeOptions(this, view)
                .setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundWhite)))
                .addIf(canEditAdmin, R.drawable.msg_admins, editingAdmin ? LocaleController.getString(R.string.EditAdminRights) : LocaleController.getString(R.string.SetAsAdmin), () -> openRightsEdit.run(0))
                .addIf(canRestrict, R.drawable.msg_permissions, LocaleController.getString(R.string.ChangePermissions), () -> {
                    if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_chatParticipantAdmin) {
                        showDialog(
                                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                                        .setTitle(LocaleController.getString(R.string.AppName))
                                        .setMessage(formatString("AdminWillBeRemoved", R.string.AdminWillBeRemoved, ContactsController.formatName(user.first_name, user.last_name)))
                                        .setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> openRightsEdit.run(1))
                                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                                        .create()
                        );
                    } else {
                        openRightsEdit.run(1);
                    }
                })
                .addIf(allowKick, R.drawable.msg_remove, LocaleController.getString(R.string.KickFromGroup), true, () -> {
                    handleKickUser(selectedUser, participant);
                })
                .setMinWidth(190)
                .show();
        return true;
    }

    private void handleKickUser(long uid, TLRPC.ChatParticipant participant) {
        if (uid != 0) {
            TLRPC.User user = getMessagesController().getUser(uid);
            getMessagesController().deleteParticipantFromChat(chatId, user);
            if (chat != null && user != null && BulletinFactory.canShowBulletin(this)) {
                BulletinFactory.createRemoveFromChatBulletin(this, user, chat.title).show();
            }
            if (chatInfo.participants.participants.remove(participant)) {
                updateChatMembersData(true);
            }
        } else {
            getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
            if (AndroidUtilities.isTablet()) {
                getNotificationCenter().postNotificationName(NotificationCenter.closeChats, -chatId);
            } else {
                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
            }
            getMessagesController().deleteParticipantFromChat(chatId, getMessagesController().getUser(getUserConfig().getClientUserId()));
            // WIP: playProfileAnimation = 0;
            finishFragment();
        }
    }

    private void handleAvatarClick(StoryViewer.PlaceProvider placeProvider) {
        if (placeProvider != null) {
            long did = getDialogId();
            StoriesController storiesController = getMessagesController().getStoriesController();
            if (storiesController.hasStories(did) || storiesController.hasUploadingStories(did) || storiesController.isLastUploadingFailed(did)) {
                getOrCreateStoryViewer().open(getContext(), did, placeProvider);
            } else if (userInfo != null && userInfo.stories != null && !userInfo.stories.stories.isEmpty() && userId != getUserConfig().clientUserId) {
                getOrCreateStoryViewer().open(getContext(), userInfo.stories, placeProvider);
            } else if (chatInfo != null && chatInfo.stories != null && !chatInfo.stories.stories.isEmpty()) {
                getOrCreateStoryViewer().open(getContext(), chatInfo.stories, placeProvider);
            } else if (headerView != null) {
                headerView.setExpanded(true, true);
            }
            return;
        }
        if (isTopic() && !getMessagesController().premiumFeaturesBlocked()) {
            ArrayList<TLRPC.TL_forumTopic> topics = getMessagesController().getTopicsController().getTopics(chatId);
            if (topics == null) return;

            TLRPC.TL_forumTopic currentTopic = null;
            for (int i = 0; currentTopic == null && i < topics.size(); ++i) {
                TLRPC.TL_forumTopic topic = topics.get(i);
                if (topic != null && topic.id == topicId) {
                    currentTopic = topic;
                }
            }
            if (currentTopic != null && currentTopic.icon_emoji_id != 0) {
                long documentId = currentTopic.icon_emoji_id;
                TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                if (document == null) return;

                Bulletin bulletin = BulletinFactory.of(this).createContainsEmojiBulletin(document, BulletinFactory.CONTAINS_EMOJI_IN_TOPIC, set -> {
                    ArrayList<TLRPC.InputStickerSet> inputSets = new ArrayList<>(1);
                    inputSets.add(set);
                    EmojiPacksAlert alert = new EmojiPacksAlert(this, getParentActivity(), getResourceProvider(), inputSets);
                    showDialog(alert);
                });
                if (bulletin != null) {
                    bulletin.show();
                    return;
                }
            }
            return;
        }
        if (headerView != null && headerView.setExpanded(true, true)) {
            return;
        }
        if (uploadedAvatarBig == null) {
            handleOpenAvatar();
        }
    }

    private boolean handleOpenAvatar() {
        if (listView == null || listView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
            return false;
        }
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user.photo != null && user.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(this);
                if (user.photo.dc_id != 0) {
                    user.photo.photo_big.dc_id = user.photo.dc_id;
                }
                PhotoViewer.getInstance().openPhoto(user.photo.photo_big, photoViewerProvider);
                return true;
            }
        } else if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat.photo != null && chat.photo.photo_big != null) {
                PhotoViewer.getInstance().setParentActivity(this);
                if (chat.photo.dc_id != 0) {
                    chat.photo.photo_big.dc_id = chat.photo.dc_id;
                }
                ImageLocation videoLocation;
                if (chatInfo != null && (chatInfo.chat_photo instanceof TLRPC.TL_photo) && !chatInfo.chat_photo.video_sizes.isEmpty()) {
                    videoLocation = ImageLocation.getForPhoto(chatInfo.chat_photo.video_sizes.get(0), chatInfo.chat_photo);
                } else {
                    videoLocation = null;
                }
                PhotoViewer.getInstance().openPhotoWithVideo(chat.photo.photo_big, videoLocation, photoViewerProvider);
                return true;
            }
        }
        return false;
    }

    private void handleBanFromGroup(TLRPC.Chat group) {
        ChatRightsEditActivity fragment = new ChatRightsEditActivity(userId, banGroupId, null, group.default_banned_rights, banGroupParticipant != null ? banGroupParticipant.banned_rights : null, "", ChatRightsEditActivity.TYPE_BANNED, true, false, null);
        fragment.setDelegate(new ChatRightsEditActivity.ChatRightsEditActivityDelegate() {
            @Override
            public void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank) {
                removeSelfFromStack();
                TLRPC.User user = getMessagesController().getUser(userId);
                if (user != null && userId != 0 && fragment.banning && fragment.getParentLayout() != null) {
                    for (BaseFragment fragment : fragment.getParentLayout().getFragmentStack()) {
                        if (fragment instanceof ChannelAdminLogActivity) {
                            ((ChannelAdminLogActivity) fragment).reloadLastMessages();
                            AndroidUtilities.runOnUIThread(() -> {
                                BulletinFactory.createRemoveFromChatBulletin(fragment, user, group.title).show();
                            });
                            return;
                        }
                    }
                }
            }
            @Override
            public void didChangeOwner(TLRPC.User user) {
                UndoView undoView = getUndoView();
                undoView.showWithAction(-chatId, group.megagroup ? UndoView.ACTION_OWNER_TRANSFERED_GROUP : UndoView.ACTION_OWNER_TRANSFERED_CHANNEL, user);
            }
        });
        presentFragment(fragment);
    }

    private PhotoViewer.PlaceProviderObject handleGetPlaceForPhoto(TLRPC.FileLocation fileLocation) {
        if (fileLocation == null || headerView == null) return null;

        ProfileHeaderView.Avatar avatar = headerView.getAvatar();
        TLRPC.FileLocation photoBig = null;
        if (userId != 0) {
            TLRPC.User user = getMessagesController().getUser(userId);
            if (user != null && user.photo != null && user.photo.photo_big != null) {
                photoBig = user.photo.photo_big;
            }
        } else if (chatId != 0) {
            TLRPC.Chat chat = getMessagesController().getChat(chatId);
            if (chat != null && chat.photo != null && chat.photo.photo_big != null) {
                photoBig = chat.photo.photo_big;
            }
        }

        if (photoBig != null && photoBig.local_id == fileLocation.local_id && photoBig.volume_id == fileLocation.volume_id && photoBig.dc_id == fileLocation.dc_id) {
            int[] coords = new int[2];
            avatar.getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
            object.parentView = avatar;
            object.imageReceiver = avatar.getImageReceiver();
            if (userId != 0) {
                object.dialogId = userId;
            } else if (chatId != 0) {
                object.dialogId = -chatId;
            }
            object.thumb = object.imageReceiver.getBitmapSafe();
            object.size = -1;
            object.radius = avatar.getImageReceiver().getRoundRadius(true);
            object.scale = avatar.getScale();
            object.canEdit = userId == getUserConfig().clientUserId;
            return object;
        }
        return null;
    }

    private static class RoundRectPopup extends ActionBarPopupWindow.ActionBarPopupWindowLayout {

        public RoundRectPopup(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, R.drawable.popup_fixed_alert, resourcesProvider);
            setFitItems(true);
        }

        private final Path path = new Path();

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            canvas.save();
            path.rewind();
            AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Path.Direction.CW);
            canvas.clipPath(path);
            boolean draw = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return draw;
        }

        private ActionBarPopupWindow show(BaseFragment fragment, View container, View anchor, float x, float y) {
            ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(this, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            popupWindow.setPauseNotifications(true);
            popupWindow.setDismissAnimationDuration(220);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            popupWindow.setFocusable(true);
            this.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.getContentView().setFocusableInTouchMode(true);

            float px = x, py = y;
            View v = anchor;
            while (v != container && v != null) {
                px += v.getX();
                py += v.getY();
                v = (View) v.getParent();
            }
            if (AndroidUtilities.isTablet()) {
                View pv = fragment.getParentLayout().getView();
                if (pv != null) {
                    px += pv.getX() + pv.getPaddingLeft();
                    py += pv.getY() + pv.getPaddingTop();
                }
            }
            px -= getMeasuredWidth() / 2f;
            popupWindow.showAtLocation(container, 0, (int) px, (int) py);
            popupWindow.dimBehind();
            return popupWindow;
        }
    }
}
