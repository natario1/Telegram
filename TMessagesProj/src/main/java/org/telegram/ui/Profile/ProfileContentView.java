package org.telegram.ui.Profile;

import android.animation.ValueAnimator;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static org.telegram.ui.Profile.ProfileContentAdapter.*;

public class ProfileContentView extends RecyclerListView implements StoriesListPlaceProvider.ClippedView {

    private final ActionBar actionBar;
    private final SharedMediaLayout sharedMediaLayout;

    public ProfileContentView(
            @NonNull ActionBar actionBar,
            @NonNull SharedMediaLayout sharedMediaLayout,
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
        setNestedScrollingEnabled(true);
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                sharedMediaLayout.setPinnedToTop(sharedMediaLayout.getY() <= 0);
                if (sharedMediaLayout.isAttachedToWindow()) {
                    sharedMediaLayout.setVisibleHeight(getMeasuredHeight() - sharedMediaLayout.getTop());
                }
            }
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                sharedMediaLayout.scrollingByUser = scrollingByUser;
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
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (sharedMediaLayout.canEditStories() && sharedMediaLayout.isActionModeShown() && sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_BOT_PREVIEWS) {
            return false;
        }
        if (sharedMediaLayout.canEditStories() && sharedMediaLayout.isActionModeShown() && sharedMediaLayout.getClosestTab() == SharedMediaLayout.TAB_STORIES) {
            return false;
        }
        if (sharedMediaLayout.giftsContainer != null && sharedMediaLayout.giftsContainer.isReordering()) {
            return false;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // WIP: updateBottomButtonY();
    }

    // SHARED MEDIA SCROLL

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) {
        boolean preventScroll = actionBar.isSearchFieldVisible();
        boolean res = preventScroll || super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
        RecyclerListView inner = sharedMediaLayout.getCurrentListView();
        int leftover = dy - consumed[1];
        if (inner != null && sharedMediaLayout.getTop() == 0 && inner.canScrollVertically(leftover)) {
            // NOTE: It's possible to understand exactly how many pixels will be consumed,
            // but it's not as important as scroll performance. We may consume too much only for one frame.
            inner.scrollBy(0, leftover);
            consumed[1] = leftover;
            res = true;
        } else if (preventScroll) {
            consumed[1] = dy;
        }
        return res;
    }

    // THEME

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

    // ROW APIS

    public int getRowPosition(int rowKind) {
        ProfileContentAdapter adapter = (ProfileContentAdapter) getAdapter();
        if (adapter == null) return -1;
        return adapter.getRows().position(rowKind);
    }

    public void scrollToRow(int rowKind, boolean animated) {
        if (!isLaidOut()) {
            post(() -> scrollToRow(rowKind, animated));
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
        int position = getRowPosition(rowKind);
        if (layoutManager == null || position < 0) return;
        if (animated) {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP, .6f);
            linearSmoothScroller.setTargetPosition(position);
            linearSmoothScroller.setOffset(-getPaddingTop());
            layoutManager.startSmoothScroll(linearSmoothScroller);
        } else {
            layoutManager.scrollToPositionWithOffset(position, -getPaddingTop());
        }
    }

    public void notifyRowChange(int rowKind) {
        int position = getRowPosition(rowKind);
        if (position >= 0 && getAdapter() != null) {
            getAdapter().notifyItemChanged(position);
        }
    }

    /** @noinspection unchecked*/
    public <T extends View> T findRowView(int rowKind) {
        int position = getRowPosition(rowKind);
        if (position >= 0 && getLayoutManager() != null) {
            return (T) getLayoutManager().findViewByPosition(position);
        }
        return null;
    }

    public void updateRows(Function<Rows, Rows> updater) {
        ProfileContentAdapter adapter = (ProfileContentAdapter) getAdapter();
        if (adapter == null) return;
        Rows rows = adapter.getRows();
        // WIP: keepingScrollPosition { ... }
        Rows newRows = updater.apply(rows);
        if (newRows != null) adapter.setRows(newRows, false);
        // WIP: AndroidUtilities.updateVisibleRows(listView);
    }

    public static class Rows {
        private int index = 0;
        private final SparseIntArray entries = new SparseIntArray();
        private final int[] positions = new int[Kinds]; // Maps kinds to positions
        private final SparseIntArray lists = new SparseIntArray(); // Entries which span more than one row - empty in most cases
        private final Map<Integer, Object> payloads = new HashMap<>(); // Short list of members, bot location, bot biometry...

        public int count() {
            return index;
        }

        public void append(int kind) {
            appendObject(kind, null, 1);
        }

        public void appendObject(int kind, Object payload, int count) {
            if (count <= 0) return;
            entries.append(index, kind);
            positions[kind] = index + 1; // We do -1 in position()
            if (payload != null) payloads.put(kind, payload);
            if (count > 1) lists.put(kind, count);
            index += count;
        }

        public <T> void appendList(int kind, List<T> list) {
            appendObject(kind, list, list.size());
        }

        public int position(int kind) {
            return positions[kind] - 1;
        }

        public int kind(int position) {
            int maybe = entries.get(position, -1);
            if (maybe >= 0) return maybe;
            for (int s = 0; s < lists.size(); s++) { // Rare
                int kind = lists.keyAt(s);
                int start = position(kind);
                int count = lists.valueAt(s);
                if (position >= start && position < start + count) return kind;
            }
            return -1;
        }

        public boolean has(int kind) {
            return position(kind) >= 0;
        }

        /** @noinspection unchecked*/
        public <T> T payload(int kind) {
            return (T) payloads.get(kind);
        }

        public Rows copy(BiPredicate<Rows, Integer> predicate) {
            Rows copy = new Rows();
            for (int i = 0; i < entries.size(); i++) {
                int kind = entries.valueAt(i);
                if (predicate.test(copy, kind)) {
                    copy.appendObject(kind, payloads.get(kind), lists.get(kind, 1));
                }
            }
            return copy;
        }

        public final static SparseIntArray VIEW_TYPES = new SparseIntArray();

        private static int Kinds = 0;
        private static int newKind(int viewType) {
            int kind = Kinds++;
            VIEW_TYPES.put(kind, viewType);
            return kind;
        }

        public final static int Members = newKind(VIEW_TYPE_USER);
        public final static int MembersShadow = newKind(VIEW_TYPE_SHADOW);
        public final static int MembersAdd = newKind(VIEW_TYPE_TEXT);

        public final static int Affiliate = newKind(VIEW_TYPE_COLORFUL_TEXT);
        public final static int AffiliateInfo = newKind(VIEW_TYPE_SHADOW_TEXT);

        public final static int ActionsBotStarsBalance = newKind(VIEW_TYPE_TEXT);
        public final static int ActionsBotTonBalance = newKind(VIEW_TYPE_TEXT);
        public final static int ActionsAddToContacts = newKind(VIEW_TYPE_TEXT);
        public final static int ActionsAddToGroupButton = newKind(VIEW_TYPE_TEXT);
        public final static int ActionsAddToGroupInfo = newKind(VIEW_TYPE_SHADOW_TEXT);
        public final static int ActionsReportReaction = newKind(VIEW_TYPE_TEXT);
        public final static int ActionsShadow = newKind(VIEW_TYPE_SHADOW);

        // SecretSettings* rows appear on encrypted chats
        public final static int SecretSettingsTimer = newKind(VIEW_TYPE_TEXT);
        public final static int SecretSettingsKey = newKind(VIEW_TYPE_TEXT);
        public final static int SecretSettingsShadow = newKind(VIEW_TYPE_SHADOW);

        // BotPermission* rows appear on bot pages
        public final static int BotPermissionHeader = newKind(VIEW_TYPE_HEADER);
        public final static int BotPermissionLocation = newKind(VIEW_TYPE_TEXT);
        public final static int BotPermissionEmojiStatus = newKind(VIEW_TYPE_TEXT);
        public final static int BotPermissionBiometry = newKind(VIEW_TYPE_TEXT);
        public final static int BotPermissionShadow = newKind(VIEW_TYPE_SHADOW);

        // Unblock rows appear in user pages when blocked
        public final static int Unblock = newKind(VIEW_TYPE_TEXT);
        public final static int UnblockShadow = newKind(VIEW_TYPE_SHADOW);

        // Generic rows for the very first section, which has extra spacing
        public final static int InfoHeader = newKind(VIEW_TYPE_SPACER);
        public final static int InfoBirthday = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int InfoPhone = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int InfoBotApp = newKind(VIEW_TYPE_BOT_APP);
        public final static int InfoBizHours = newKind(VIEW_TYPE_HOURS);
        public final static int InfoBizLocation = newKind(VIEW_TYPE_LOCATION);
        public final static int InfoUserAbout = newKind(VIEW_TYPE_ABOUT_LINK);
        public final static int InfoChatAbout = newKind(VIEW_TYPE_ABOUT_LINK);
        public final static int InfoUsername = newKind(VIEW_TYPE_TEXT_DETAIL_MULTILINE);
        public final static int InfoLocation = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int InfoFooter = newKind(VIEW_TYPE_SPACER);
        public final static int InfoShadow = newKind(VIEW_TYPE_SHADOW_TEXT);

        // public final static int SendMessage = newKind(VIEW_TYPE_TEXT); // Moved to header
        // public final static int NotificationsSimple = newKind(VIEW_TYPE_NOTIFICATIONS_CHECK_SIMPLE); // Moved to header

        // ChannelOptions* rows appear on managed channels
        public final static int ChannelOptionsSubscribers = newKind(VIEW_TYPE_TEXT);
        public final static int ChannelOptionsSubscribersRequests = newKind(VIEW_TYPE_TEXT);
        public final static int ChannelOptionsAdministrators = newKind(VIEW_TYPE_TEXT);
        public final static int ChannelOptionsBalance = newKind(VIEW_TYPE_TEXT);
        public final static int ChannelOptionsSettings = newKind(VIEW_TYPE_TEXT);
        public final static int ChannelOptionsShadow = newKind(VIEW_TYPE_SHADOW);
        public final static int ChannelOptionsBlockedUsers = newKind(VIEW_TYPE_TEXT);

        // SharedMedia* rows appear on most pages
        public final static int SharedMediaPrefix = newKind(VIEW_TYPE_SHADOW);
        public final static int SharedMedia = newKind(VIEW_TYPE_SHARED_MEDIA);
        public final static int Filler = newKind(VIEW_TYPE_FILLER); // used when there's no SharedMedia

        // PersonalChannel may appear at the top of user pages
        public final static int PersonalChannel = newKind(VIEW_TYPE_CHANNEL);
        public final static int PersonalChannelShadow = newKind(VIEW_TYPE_SHADOW);

        // My* rows appear in my settings
        public final static int MyHeader = newKind(VIEW_TYPE_SPACER);
        public final static int MyFooter = newKind(VIEW_TYPE_SPACER);
        public final static int MyUsername = newKind(VIEW_TYPE_TEXT_DETAIL_MULTILINE);
        public final static int MyBio = newKind(VIEW_TYPE_ABOUT_LINK);
        public final static int MyPhoneNumber = newKind(VIEW_TYPE_TEXT_DETAIL);
        public final static int MyShadow = newKind(VIEW_TYPE_SHADOW);
        public final static int MyVersion = newKind(VIEW_TYPE_SHADOW_VERSION);

        // Suggestion* rows appear in my settings
        public final static int SuggestionShadow = newKind(VIEW_TYPE_SHADOW);
        public final static int SuggestionGrace = newKind(VIEW_TYPE_SUGGESTION);
        public final static int SuggestionPhone = newKind(VIEW_TYPE_SUGGESTION);
        public final static int SuggestionPassword = newKind(VIEW_TYPE_SUGGESTION);

        // MySettings* rows appear in my settings
        public final static int MySettingsHeader = newKind(VIEW_TYPE_HEADER);
        public final static int MySettingsPrivacy = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsNotification = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsLanguage = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsFilters = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsChat = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsData = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsDevices = newKind(VIEW_TYPE_TEXT);
        public final static int MySettingsSection = newKind(VIEW_TYPE_SHADOW);
        public final static int MySettingsLiteMode = newKind(VIEW_TYPE_TEXT);

        // Help* rows appear in my settings
        public final static int HelpHeader = newKind(VIEW_TYPE_HEADER);
        public final static int HelpSection = newKind(VIEW_TYPE_SHADOW);
        public final static int HelpQuestion = newKind(VIEW_TYPE_TEXT);
        public final static int HelpPolicy = newKind(VIEW_TYPE_TEXT);
        public final static int HelpFaq = newKind(VIEW_TYPE_TEXT);

        // Features* rows appear in my settings
        public final static int FeaturesPremium = newKind(VIEW_TYPE_PREMIUM_TEXT_CELL);
        public final static int FeaturesGift = newKind(VIEW_TYPE_TEXT);
        public final static int FeaturesSection = newKind(VIEW_TYPE_SHADOW);
        public final static int FeaturesStars = newKind(VIEW_TYPE_STARS_TEXT_CELL);
        public final static int FeaturesBusiness = newKind(VIEW_TYPE_TEXT);

        // Debug* rows appear in my settings
        public final static int DebugHeader = newKind(VIEW_TYPE_HEADER);
        public final static int DebugClearLogs = newKind(VIEW_TYPE_TEXT);
        public final static int DebugSendLogs = newKind(VIEW_TYPE_TEXT);
        public final static int DebugSendLastLogs = newKind(VIEW_TYPE_TEXT);
        public final static int DebugSwitchBackend = newKind(VIEW_TYPE_TEXT);
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

    public static class RowDiffer extends DiffUtil.Callback {

        private final Rows oldRows;
        private final Rows newRows;

        public RowDiffer(Rows oldRows, Rows newRows) {
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
                List<TLRPC.ChatParticipant> oldMembers = oldRows.payload(Rows.Members);
                List<TLRPC.ChatParticipant> newMembers = newRows.payload(Rows.Members);
                return oldMembers.get(oldIndex).user_id == newMembers.get(newIndex).user_id;
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (!areItemsTheSame(oldItemPosition, newItemPosition)) return false;
            int kind = newRows.kind(newItemPosition);
            return oldRows.payload(kind) == newRows.payload(kind);
        }
    }
}
