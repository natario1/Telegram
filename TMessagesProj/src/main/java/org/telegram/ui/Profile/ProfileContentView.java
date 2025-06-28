package org.telegram.ui.Profile;

import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Profile.ProfileContentAdapter.*;

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

    public void getThemeDescriptions(ArrayList<ThemeDescription> arrayList, ThemeDescription.ThemeDescriptionDelegate delegate) {
        if (sharedMediaLayout != null) {
            arrayList.addAll(sharedMediaLayout.getThemeDescriptions());
        }

        arrayList.add(new ThemeDescription(this, 0, null, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(this, 0, null, null, null, null, Theme.key_windowBackgroundGray));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText2));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{TextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));

        arrayList.add(new ThemeDescription(this, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        arrayList.add(new ThemeDescription(this, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        arrayList.add(new ThemeDescription(this, 0, new Class[]{SettingsSuggestionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{SettingsSuggestionCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_LINKCOLOR, new Class[]{SettingsSuggestionCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{SettingsSuggestionCell.class}, new String[]{"yesButton"}, null, null, null, Theme.key_featuredStickers_buttonText));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{SettingsSuggestionCell.class}, new String[]{"yesButton"}, null, null, null, Theme.key_featuredStickers_addButton));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{SettingsSuggestionCell.class}, new String[]{"yesButton"}, null, null, null, Theme.key_featuredStickers_addButtonPressed));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{SettingsSuggestionCell.class}, new String[]{"noButton"}, null, null, null, Theme.key_featuredStickers_buttonText));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{SettingsSuggestionCell.class}, new String[]{"noButton"}, null, null, null, Theme.key_featuredStickers_addButton));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{SettingsSuggestionCell.class}, new String[]{"noButton"}, null, null, null, Theme.key_featuredStickers_addButtonPressed));

        arrayList.add(new ThemeDescription(this, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{UserCell.class}, new String[]{"adminTextView"}, null, null, null, Theme.key_profile_creatorIcon));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{UserCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, delegate, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, delegate, Theme.key_windowBackgroundWhiteBlueText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{UserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));

        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{AboutLinkCell.class}, Theme.profile_aboutTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_LINKCOLOR, new Class[]{AboutLinkCell.class}, Theme.profile_aboutTextPaint, null, null, Theme.key_windowBackgroundWhiteLinkText));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{AboutLinkCell.class}, Theme.linkSelectionPaint, null, null, Theme.key_windowBackgroundWhiteLinkSelection));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(this, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(this, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
    }

    public void scrollTo(int rowKind, boolean animated) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        ProfileContentAdapter adapter = (ProfileContentAdapter) getAdapter();
        if (adapter == null || layoutManager == null) return;
        int position = adapter.getRows().position(rowKind);
        if (position >= 0) {
            if (animated) {
                LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .6f);
                linearSmoothScroller.setTargetPosition(position);
                linearSmoothScroller.setOffset(-getPaddingTop());
                layoutManager.startSmoothScroll(linearSmoothScroller);
            } else {
                layoutManager.scrollToPositionWithOffset(position, -getPaddingTop());
            }
        }
    }

    public void notifyChangeTo(int rowKind) {
        ProfileContentAdapter adapter = (ProfileContentAdapter) getAdapter();
        if (adapter == null) return;
        int position = adapter.getRows().position(rowKind);
        if (position >= 0) adapter.notifyItemChanged(position);
    }

    public RLottieImageView findSetAvatarImage() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        ProfileContentAdapter adapter = (ProfileContentAdapter) getAdapter();
        if (adapter == null || layoutManager == null) return null;
        int position = adapter.getRows().position(Rows.SetAvatar);
        if (position >= 0) {
            TextCell view = (TextCell) layoutManager.findViewByPosition(position);
            if (view != null) return view.getImageView();
        }
        return null;
    }

    public void computeBirthdaySourcePosition(PointF outPoint) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        ProfileContentAdapter adapter = (ProfileContentAdapter) getAdapter();
        if (adapter == null || layoutManager == null) return;
        int position = adapter.getRows().position(Rows.Birthday);
        if (position > 0) {
            TextDetailCell cell = (TextDetailCell) layoutManager.findViewByPosition(position);
            if (cell != null) {
                outPoint.set(
                        getX() + cell.getX() + cell.textView.getX() + dp(12),
                        getY() + cell.getY() + cell.textView.getY() + cell.textView.getMeasuredHeight() / 2f
                );
            }
        }
    }

    public static class Rows {
        private int index = 0;
        private final SparseIntArray data = new SparseIntArray();
        private List<TLRPC.ChatParticipant> members = Collections.emptyList();

        public int count() {
            return index;
        }

        public void append(int kind) {
            data.put(kind, index);
            index++;
        }

        public void appendMembers(List<TLRPC.ChatParticipant> members) {
            this.members = members;
            for (int i = 0; i < members.size(); i++) {
                append(Members);
            }
        }

        public int position(int kind) {
            return data.get(kind, -1);
        }

        public int kind(int position) {
            return data.keyAt(position);
        }

        public boolean has(int kind) {
            return position(kind) >= 0;
        }

        private final static SparseIntArray VIEW_TYPES = new SparseIntArray();

        private static int Kinds = 0;
        public static int newKind(int viewType) {
            int kind = ++Kinds;
            VIEW_TYPES.put(kind, viewType);
            return kind;
        }

        public final static int AddToContacts = newKind(VIEW_TYPE_TEXT);
        public final static int AddToGroupButton = newKind(VIEW_TYPE_TEXT);
        public final static int AddToGroupInfo = newKind(VIEW_TYPE_ADDTOGROUP_INFO);
        public final static int Affiliate = newKind(VIEW_TYPE_COLORFUL_TEXT);
        public final static int AddMember = newKind(VIEW_TYPE_TEXT);
        public final static int Administrators = newKind(VIEW_TYPE_TEXT);
        public final static int BalanceDivider = newKind(VIEW_TYPE_SHADOW);
        public final static int BlockedUsers = newKind(VIEW_TYPE_TEXT);
        public final static int Bio = newKind(VIEW_TYPE_ABOUT_LINK);
        public final static int Birthday = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int BizHours = newKind(VIEW_TYPE_HOURS);
        public final static int BizLocation = newKind(VIEW_TYPE_LOCATION);
        public final static int BotStarsBalance = newKind(VIEW_TYPE_TEXT);
        public final static int BotTonBalance = newKind(VIEW_TYPE_TEXT);
        public final static int BotApp = newKind(VIEW_TYPE_BOT_APP);
        public final static int BotPermissionHeader = newKind(VIEW_TYPE_HEADER);
        public final static int BotPermissionLocation = newKind(VIEW_TYPE_TEXT);
        public final static int BotPermissionEmojiStatus = newKind(VIEW_TYPE_TEXT);
        public final static int BotPermissionBiometry = newKind(VIEW_TYPE_TEXT);
        public final static int BotPermissionDivider = newKind(VIEW_TYPE_SHADOW);
        // public final static int BottomPadding = newKind(VIEW_TYPE_BOTTOM_PADDING);
        public final static int Business = newKind(VIEW_TYPE_TEXT);
        public final static int Channel = newKind(VIEW_TYPE_CHANNEL);
        public final static int ChannelDivider = newKind(VIEW_TYPE_SHADOW);
        public final static int ChannelInfo = newKind(VIEW_TYPE_ABOUT_LINK);
        public final static int ChannelBalance = newKind(VIEW_TYPE_TEXT);
        public final static int ChannelBalanceSection = newKind(VIEW_TYPE_SHADOW);
        public final static int Chat = newKind(VIEW_TYPE_TEXT);
        public final static int ClearLogs = newKind(VIEW_TYPE_TEXT);
        public final static int Data = newKind(VIEW_TYPE_TEXT);
        public final static int DebugHeader = newKind(VIEW_TYPE_HEADER);
        public final static int Devices = newKind(VIEW_TYPE_TEXT);
        public final static int DevicesSection = newKind(VIEW_TYPE_SHADOW);
        public final static int Empty = newKind(VIEW_TYPE_EMPTY);
        public final static int Faq = newKind(VIEW_TYPE_TEXT);
        public final static int Filters = newKind(VIEW_TYPE_TEXT);
        public final static int GraceSuggestionSection = newKind(VIEW_TYPE_SHADOW);
        public final static int GraceSuggestion = newKind(VIEW_TYPE_SUGGESTION);
        public final static int HelpHeader = newKind(VIEW_TYPE_HEADER);
        public final static int HelpSection = newKind(VIEW_TYPE_SHADOW);
        public final static int InfoHeader = newKind(VIEW_TYPE_HEADER);
        public final static int InfoSection = newKind(VIEW_TYPE_SHADOW_TEXT);
        public final static int InfoAffiliate = newKind(VIEW_TYPE_SHADOW_TEXT);
        public final static int Join = newKind(VIEW_TYPE_TEXT);
        public final static int Language = newKind(VIEW_TYPE_TEXT);
        public final static int LiteMode = newKind(VIEW_TYPE_TEXT);
        public final static int Location = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int LastSection = newKind(VIEW_TYPE_SHADOW);
        public final static int MembersHeader = newKind(VIEW_TYPE_HEADER);
        public final static int Members = newKind(VIEW_TYPE_USER);
        public final static int MembersSection = newKind(VIEW_TYPE_SHADOW);
        public final static int Notification = newKind(VIEW_TYPE_TEXT);
        public final static int NotificationsDivider = newKind(VIEW_TYPE_DIVIDER);
        public final static int Notifications = newKind(VIEW_TYPE_NOTIFICATIONS_CHECK);
        public final static int NotificationsSimple = newKind(VIEW_TYPE_NOTIFICATIONS_CHECK_SIMPLE);
        public final static int NumberSection = newKind(VIEW_TYPE_HEADER);
        public final static int Number = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int PhoneSuggestionSection = newKind(VIEW_TYPE_SHADOW);
        public final static int PhoneSuggestion = newKind(VIEW_TYPE_SUGGESTION);
        public final static int PasswordSuggestionSection = newKind(VIEW_TYPE_SHADOW);
        public final static int PasswordSuggestion = newKind(VIEW_TYPE_SUGGESTION);
        public final static int Phone = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int Premium = newKind(VIEW_TYPE_PREMIUM_TEXT_CELL);
        public final static int PremiumGifting = newKind(VIEW_TYPE_TEXT);
        public final static int PremiumSections = newKind(VIEW_TYPE_SHADOW);
        public final static int Policy = newKind(VIEW_TYPE_TEXT);
        public final static int Privacy = newKind(VIEW_TYPE_TEXT);
        public final static int Question = newKind(VIEW_TYPE_TEXT);
        public final static int Report = newKind(VIEW_TYPE_TEXT);
        public final static int ReportReaction = newKind(VIEW_TYPE_TEXT);
        public final static int ReportDivider = newKind(VIEW_TYPE_SHADOW);
        public final static int Settings = newKind(VIEW_TYPE_TEXT);
        public final static int SettingsTimer = newKind(VIEW_TYPE_TEXT);
        public final static int SettingsSection = newKind(VIEW_TYPE_SHADOW);
        public final static int SettingsKey = newKind(VIEW_TYPE_TEXT);
        public final static int SettingsSection2 = newKind(VIEW_TYPE_HEADER);
        public final static int SetAvatar = newKind(VIEW_TYPE_TEXT);
        public final static int SetAvatarSection = newKind(VIEW_TYPE_SHADOW);
        public final static int SetUsername = newKind(VIEW_TYPE_TEXT_DETAIL_MULTILINE);
        public final static int Stickers = newKind(VIEW_TYPE_TEXT);
        public final static int SendLogs = newKind(VIEW_TYPE_TEXT);
        public final static int SendLastLogs = newKind(VIEW_TYPE_TEXT);
        public final static int SwitchBackend = newKind(VIEW_TYPE_TEXT);
        public final static int SendMessage = newKind(VIEW_TYPE_TEXT);
        public final static int Subscribers = newKind(VIEW_TYPE_TEXT);
        public final static int SubscribersRequests = newKind(VIEW_TYPE_TEXT);
        public final static int Stars = newKind(VIEW_TYPE_STARS_TEXT_CELL);
        public final static int SecretSettingsSection = newKind(VIEW_TYPE_SHADOW);
        public final static int SharedMedia = newKind(VIEW_TYPE_SHARED_MEDIA);
        public final static int UserInfo = newKind(VIEW_TYPE_ABOUT_LINK);
        public final static int Username = newKind(VIEW_TYPE_TEXT_DETAIL_MULTILINE);
        public final static int Unblock = newKind(VIEW_TYPE_TEXT);
        public final static int Version = newKind(VIEW_TYPE_VERSION);
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

    private static class RowDiffer extends DiffUtil.Callback {

        private final Rows oldRows;
        private final Rows newRows;

        private RowDiffer(Rows oldRows, Rows newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.count();
        }

        @Override
        public int getNewListSize() {
            return newRows.count();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            int kind = newRows.kind(newItemPosition);
            if (kind == oldRows.kind(oldItemPosition)) {
                if (kind != Rows.Members) return true;
                int oldIndex = oldItemPosition - oldRows.position(Rows.Members);
                int newIndex = newItemPosition - newRows.position(Rows.Members);
                return oldRows.members.get(oldIndex).user_id == newRows.members.get(newIndex).user_id;
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }
    }
}
