package org.telegram.ui.Profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.*;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.ProfileHoursCell;
import org.telegram.ui.Business.ProfileLocationCell;
import org.telegram.ui.Cells.*;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.Components.*;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.ProfilePremiumCell;
import org.telegram.ui.ProfileActivityReplacement;
import org.telegram.ui.Stars.BotStarsController;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.UserInfoActivity;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.ui.bots.BotBiometry;
import org.telegram.ui.bots.BotLocation;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Profile.ProfileContentView.*;

public class ProfileContentAdapter extends RecyclerListView.SelectionAdapter {

    public final static int VIEW_TYPE_HEADER = 1,
            VIEW_TYPE_TEXT_DETAIL = 2,
            VIEW_TYPE_ABOUT_LINK = 3,
            VIEW_TYPE_TEXT = 4,
            // VIEW_TYPE_DIVIDER = 5,
            // VIEW_TYPE_NOTIFICATIONS_CHECK = 6,
            VIEW_TYPE_SHADOW = 7,
            VIEW_TYPE_USER = 8,
            // VIEW_TYPE_EMPTY = 11,
            // VIEW_TYPE_BOTTOM_PADDING = 12,
            VIEW_TYPE_SHARED_MEDIA = 13,
            VIEW_TYPE_SHADOW_VERSION = 14,
            VIEW_TYPE_SUGGESTION = 15,
            VIEW_TYPE_PREMIUM_TEXT_CELL = 18,
            VIEW_TYPE_TEXT_DETAIL_MULTILINE = 19,
            // VIEW_TYPE_NOTIFICATIONS_CHECK_SIMPLE = 20,
            VIEW_TYPE_LOCATION = 21,
            VIEW_TYPE_HOURS = 22,
            VIEW_TYPE_CHANNEL = 23,
            VIEW_TYPE_STARS_TEXT_CELL = 24,
            VIEW_TYPE_BOT_APP = 25,
            VIEW_TYPE_SHADOW_TEXT = 26,
            VIEW_TYPE_COLORFUL_TEXT = 27,
            VIEW_TYPE_SPACER = 28,
            VIEW_TYPE_FILLER = 29;

    private final ProfileActivityReplacement fragment;
    private final Theme.ResourcesProvider resourcesProvider;

    private Rows rows = new Rows();
    private CharacterStyle loadingSpan;
    private final HashMap<TLRPC.TL_username, ClickableSpan> usernameSpans = new HashMap<>();
    private RecyclerView owner;
    private RLottieDrawable cellCameraDrawable;

    public ProfileContentAdapter(ProfileActivityReplacement fragment) {
        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
    }

    public Rows getRows() {
        return rows;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setRows(Rows newRows, boolean reload) {
        if (newRows == rows) return;
        if (owner != null && owner.isComputingLayout()) {
            owner.post(() -> setRows(newRows, reload));
            return;
        }
        if (reload || rows.count() == 0 || newRows.count() == 0 || owner == null) {
            this.rows = newRows;
            if (owner != null) notifyDataSetChanged();
        } else {
            RowDiffer differ = new RowDiffer(rows, newRows);
            this.rows = newRows;
            try {
                DiffUtil.calculateDiff(differ).dispatchUpdatesTo(this);
            } catch (Exception e) {
                FileLog.e(e);
                notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        owner = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        owner = null;
    }

    @Override
    public int getItemCount() {
        return rows.count();
    }

    @Override
    public int getItemViewType(int position) {
        return Rows.VIEW_TYPES.get(rows.kind(position));
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        boolean hasNotification = rows.has(Rows.MySettingsNotification);
        if (hasNotification) {
            int kind = rows.kind(holder.getAdapterPosition());
            return kind == Rows.MySettingsNotification || kind == Rows.MyPhoneNumber || kind == Rows.MySettingsPrivacy
                    || kind == Rows.MySettingsLanguage || kind == Rows.MyUsername || kind == Rows.MyBio
                    || kind == Rows.MyVersion || kind == Rows.MySettingsData || kind == Rows.MySettingsChat
                    || kind == Rows.HelpQuestion || kind == Rows.MySettingsDevices || kind == Rows.MySettingsFilters
                    || kind == Rows.HelpFaq || kind == Rows.HelpPolicy || kind == Rows.FeaturesStars
                    || kind == Rows.DebugSendLogs || kind == Rows.DebugSendLastLogs || kind == Rows.DebugClearLogs
                    || kind == Rows.DebugSwitchBackend || kind == Rows.ActionsAddToGroupButton
                    || kind == Rows.FeaturesPremium || kind == Rows.FeaturesGift || kind == Rows.FeaturesBusiness
                    || kind == Rows.MySettingsLiteMode || kind == Rows.InfoBirthday || kind == Rows.PersonalChannel;
        }
        if (holder.itemView instanceof UserCell) {
            UserCell userCell = (UserCell) holder.itemView;
            Object object = userCell.getCurrentObject();
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                if (UserObject.isUserSelf(user)) return false;
            }
        }
        int type = holder.getItemViewType();
        return type != VIEW_TYPE_HEADER && type != 5 && type != VIEW_TYPE_SHADOW &&
                type != VIEW_TYPE_SPACER && type != VIEW_TYPE_FILLER &&
                type != 11 && /* type != VIEW_TYPE_BOTTOM_PADDING && */ type != VIEW_TYPE_SHARED_MEDIA &&
                type != 9 && type != 10 && type != VIEW_TYPE_BOT_APP; // These are legacy ones, left for compatibility
    }

    private void setLoadingSpan(CharacterStyle span) {
        if (loadingSpan == span) return;
        loadingSpan = span;
        if (owner == null) return;
        AndroidUtilities.forEachViews(owner, view -> {
            if (view instanceof TextDetailCell) {
                ((TextDetailCell) view).textView.setLoading(loadingSpan);
                ((TextDetailCell) view).valueTextView.setLoading(loadingSpan);
            }
        });
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof TextDetailCell) {
            ((TextDetailCell) holder.itemView).textView.setLoading(loadingSpan);
            ((TextDetailCell) holder.itemView).valueTextView.setLoading(loadingSpan);
        }
    }


    public ClickableSpan makeUsernameLinkSpan(TLRPC.TL_username usernameObj) {
        ClickableSpan span = usernameSpans.get(usernameObj);
        if (span != null) return span;
        span = new ClickableSpan() {
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setColor(ds.linkColor);
            }

            @Override
            public void onClick(@NonNull View view) {
                if (usernameObj.editable) {
                    if (loadingSpan == this) return;
                    setLoadingSpan(this);
                    fragment.handleUsernameSpanClick(usernameObj, () -> {
                        setLoadingSpan(null);
                    });
                } else {
                    setLoadingSpan(null);
                    fragment.handleUsernameSpanClick(usernameObj, () -> {
                    });
                }
            }
        };
        usernameSpans.put(usernameObj, span);
        return span;
    }

    private CharSequence alsoUsernamesString(String originalUsername, ArrayList<TLRPC.TL_username> alsoUsernames, CharSequence fallback) {
        if (alsoUsernames == null) return fallback;
        alsoUsernames = new ArrayList<>(alsoUsernames);
        for (int i = 0; i < alsoUsernames.size(); ++i) {
            if (!alsoUsernames.get(i).active || originalUsername != null && originalUsername.equals(alsoUsernames.get(i).username)) {
                alsoUsernames.remove(i--);
            }
        }
        if (alsoUsernames.isEmpty()) return fallback;
        SpannableStringBuilder usernames = new SpannableStringBuilder();
        for (int i = 0; i < alsoUsernames.size(); ++i) {
            TLRPC.TL_username usernameObj = alsoUsernames.get(i);
            final String usernameRaw = usernameObj.username;
            SpannableString username = new SpannableString("@" + usernameRaw);
            username.setSpan(makeUsernameLinkSpan(usernameObj), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            username.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_chat_messageLinkIn)), 0, username.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            usernames.append(username);
            if (i < alsoUsernames.size() - 1) {
                usernames.append(", ");
            }
        }
        String string = LocaleController.getString(R.string.UsernameAlso);
        SpannableStringBuilder finalString = new SpannableStringBuilder(string);
        final String toFind = "%1$s";
        int index = string.indexOf(toFind);
        if (index >= 0) {
            finalString.replace(index, index + toFind.length(), usernames);
        }
        return finalString;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
        View view;
        switch (viewType) {
            case VIEW_TYPE_HEADER: {
                view = new HeaderCell(context, 23, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_SPACER: {
                view = new View(context);
                break;
            }
            case VIEW_TYPE_FILLER: {
                view = new View(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        if (owner == null) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            return;
                        }
                        int n = owner.getChildCount();
                        if (n != getItemCount()) {
                            setMeasuredDimension(owner.getMeasuredWidth(), getMeasuredHeight());
                            return;
                        }
                        int rest = 0;
                        for (int i = 0; i < n; i++) {
                            View view = owner.getChildAt(i);
                            int p = owner.getChildAdapterPosition(view);
                            if (p >= 0 && p != rows.position(Rows.Filler)) {
                                rest += view.getMeasuredHeight();
                            }
                        }
                        int h = Math.max(0, owner.getMeasuredHeight() - rest);
                        setMeasuredDimension(owner.getMeasuredWidth(), h);
                    }
                };
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
                break;
            }
            case VIEW_TYPE_TEXT_DETAIL_MULTILINE:
            case VIEW_TYPE_TEXT_DETAIL: {
                final TextDetailCell textDetailCell = new TextDetailCell(context, resourcesProvider, viewType == VIEW_TYPE_TEXT_DETAIL_MULTILINE);
                textDetailCell.setContentDescriptionValueFirst(true);
                view = textDetailCell;
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_ABOUT_LINK: {
                view = new AboutLinkCell(context, fragment, resourcesProvider) {
                    @Override
                    protected void didPressUrl(String url, Browser.Progress progress) {
                        fragment.handleOpenUrl(url, progress);
                    }

                    @Override
                    protected void didResizeEnd() {
                        LinearLayoutManager layoutManager = owner == null ? null : (LinearLayoutManager) owner.getLayoutManager();
                        if (layoutManager != null) layoutManager.mIgnoreTopPadding = false;
                    }

                    @Override
                    protected void didResizeStart() {
                        LinearLayoutManager layoutManager = owner == null ? null : (LinearLayoutManager) owner.getLayoutManager();
                        if (layoutManager != null) layoutManager.mIgnoreTopPadding = true;
                    }
                };
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_TEXT: {
                view = new TextCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            /* Moved to header case VIEW_TYPE_NOTIFICATIONS_CHECK: {
                view = new NotificationsCheckCell(context, 23, 70, false, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            } */
            /* Moved to header case VIEW_TYPE_NOTIFICATIONS_CHECK_SIMPLE: {
                view = new TextCheckCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            } */
            case VIEW_TYPE_SHADOW: {
                view = new ShadowSectionCell(context, 12, getThemedColor(Theme.key_windowBackgroundGray), resourcesProvider);
                break;
            }
            case VIEW_TYPE_SHADOW_TEXT: {
                view = new TextInfoPrivacyCell(context, resourcesProvider);
                break;
            }
            case VIEW_TYPE_COLORFUL_TEXT: {
                view = new AffiliateProgramFragment.ColorfulTextCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_USER: {
                boolean hasAddMember = rows.has(Rows.MembersAdd);
                view = new UserCell(context, hasAddMember ? 6 : 9, 0, true, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_LOCATION: {
                view = new ProfileLocationCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_HOURS: {
                view = new ProfileHoursCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_PREMIUM_TEXT_CELL:
            case VIEW_TYPE_STARS_TEXT_CELL: {
                view = new ProfilePremiumCell(context, viewType == VIEW_TYPE_PREMIUM_TEXT_CELL ? 0 : 1, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_CHANNEL: {
                view = new ProfileChannelCell(fragment);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_BOT_APP: {
                FrameLayout frameLayout = new FrameLayout(context);
                ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
                button.setText(LocaleController.getString(R.string.ProfileBotOpenApp), false);
                button.setOnClickListener(v -> fragment.handleOpenBotApp());
                frameLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 18, 14, 18, 14));
                view = frameLayout;
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            }
            case VIEW_TYPE_SUGGESTION: {
                view = new SettingsSuggestionCell(context, resourcesProvider) {
                    @Override protected void onYesClick(int type) { fragment.handleSuggestionClick(type, true); }
                    @Override protected void onNoClick(int type) { fragment.handleSuggestionClick(type, false); }
                };
                break;
            }
            case VIEW_TYPE_SHARED_MEDIA: {
                ViewGroup oldParent = (ViewGroup) fragment.sharedMediaLayout.getParent();
                if (oldParent != null) oldParent.removeView(fragment.sharedMediaLayout);
                view = fragment.sharedMediaLayout;
                params = null;
                break;
            }

            case VIEW_TYPE_SHADOW_VERSION:
            default: {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, 10, resourcesProvider);
                cell.getTextView().setGravity(Gravity.CENTER_HORIZONTAL);
                cell.getTextView().setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
                cell.getTextView().setMovementMethod(null);
                try {
                    PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                    int code = pInfo.versionCode / 10;
                    String abi = "";
                    switch (pInfo.versionCode % 10) {
                        case 1: case 2: abi = "store bundled"; break;
                        default: abi = ApplicationLoader.isStandaloneBuild() ? "direct" : "universal";
                    }
                    abi += " " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                    cell.setText(LocaleController.formatString("TelegramVersion", R.string.TelegramVersion, String.format(Locale.US, "v%s (%d) %s", pInfo.versionName, code, abi)));
                } catch (Exception e) {
                    FileLog.e(e);
                }
                cell.getTextView().setPadding(0, dp(14), 0, dp(14));
                view = cell;
                Drawable shadow = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, getThemedColor(Theme.key_windowBackgroundGrayShadow));
                Drawable background = new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combined = new CombinedDrawable(background, shadow, 0, 0);
                combined.setFullsize(true);
                view.setBackground(combined);
                break;
            }
        }
        if (params != null) {
            view.setLayoutParams(params);
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int pos) {
        int kind = rows.kind(pos);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_HEADER: {
                HeaderCell headerCell = (HeaderCell) holder.itemView;
                if (kind == Rows.MySettingsHeader) {
                    headerCell.setText(LocaleController.getString(R.string.SETTINGS));
                } else if (kind == Rows.HelpHeader) {
                    headerCell.setText(LocaleController.getString(R.string.SettingsHelp));
                } else if (kind == Rows.DebugHeader) {
                    headerCell.setText(LocaleController.getString(R.string.SettingsDebug));
                } else if (kind == Rows.BotPermissionHeader) {
                    headerCell.setText(LocaleController.getString(R.string.BotProfilePermissions));
                }
                headerCell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
                break;
            }
            case VIEW_TYPE_SPACER: {
                Integer spacing = rows.payload(kind);
                holder.itemView.setMinimumHeight(spacing != null ? spacing : dp(5));
                break;
            }
            case VIEW_TYPE_TEXT_DETAIL_MULTILINE:
            case VIEW_TYPE_TEXT_DETAIL: {
                TextDetailCell detailCell = (TextDetailCell) holder.itemView;
                boolean containsQr = false;
                boolean containsGift = false;
                if (kind == Rows.InfoBirthday) {
                    TL_account.TL_birthday birthday = rows.payload(kind);
                    if (birthday != null) {
                        final boolean today = BirthdayController.isToday(fragment.getMessagesController().getUserFull(fragment.getUserId()));
                        final boolean withYear = (birthday.flags & 1) != 0;
                        final int age = withYear ? Period.between(LocalDate.of(birthday.year, birthday.month, birthday.day), LocalDate.now()).getYears() : -1;
                        String text = UserInfoActivity.birthdayString(birthday);
                        if (withYear) {
                            text = LocaleController.formatPluralString(today ? "ProfileBirthdayTodayValueYear" : "ProfileBirthdayValueYear", age, text);
                        } else {
                            text = LocaleController.formatString(today ? R.string.ProfileBirthdayTodayValue : R.string.ProfileBirthdayValue, text);
                        }

                        detailCell.setTextAndValue(
                                Emoji.replaceWithRestrictedEmoji(text, detailCell.textView, () -> onBindViewHolder(holder, holder.getAdapterPosition())),
                                LocaleController.getString(today ? R.string.ProfileBirthdayToday : R.string.ProfileBirthday),
                                fragment.getTopicId() != 0 || rows.has(Rows.InfoBizHours) || rows.has(Rows.InfoBizLocation)
                        );
                        containsGift = !fragment.isMyProfile() && today && !fragment.getMessagesController().premiumPurchaseBlocked();
                    }
                } else if (kind == Rows.InfoPhone) {
                    String text;
                    TLRPC.User user = fragment.getMessagesController().getUser(fragment.getUserId());
                    String phoneNumber;
                    String vcardPhone = PhoneFormat.stripExceptNumbers(fragment.getArguments().getString("vcard_phone"));
                    if (user != null && !TextUtils.isEmpty(vcardPhone)) {
                        text = PhoneFormat.getInstance().format("+" + vcardPhone);
                        phoneNumber = vcardPhone;
                    } else if (user != null && !TextUtils.isEmpty(user.phone)) {
                        text = PhoneFormat.getInstance().format("+" + user.phone);
                        phoneNumber = user.phone;
                    } else {
                        text = LocaleController.getString(R.string.PhoneHidden);
                        phoneNumber = null;
                    }
                    boolean isFragmentPhoneNumber = phoneNumber != null && phoneNumber.matches("888\\d{8}");
                    detailCell.setTag(isFragmentPhoneNumber);
                    detailCell.setTextAndValue(text, LocaleController.getString(isFragmentPhoneNumber ? R.string.AnonymousNumber : R.string.PhoneMobile), false);
                } else if (kind == Rows.InfoUsername) {
                    String username = null;
                    CharSequence text;
                    CharSequence value;
                    ArrayList<TLRPC.TL_username> usernames = new ArrayList<>();
                    TLObject obj = rows.payload(kind);
                    if (obj instanceof TLRPC.User) {
                        final TLRPC.User user = (TLRPC.User) obj;
                        usernames.addAll(user.usernames);
                        TLRPC.TL_username usernameObj = null;
                        if (!TextUtils.isEmpty(user.username)) {
                            usernameObj = DialogObject.findUsername(user.username, usernames);
                            username = user.username;
                        }
                        usernames = new ArrayList<>(user.usernames);
                        if (TextUtils.isEmpty(username)) {
                            for (TLRPC.TL_username u : usernames) {
                                if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                                    usernameObj = u;
                                    username = u.username;
                                    break;
                                }
                            }
                        }
                        value = LocaleController.getString(R.string.Username);
                        if (username != null) {
                            text = "@" + username;
                            if (usernameObj != null && !usernameObj.editable) {
                                text = new SpannableString(text);
                                ((SpannableString) text).setSpan(makeUsernameLinkSpan(usernameObj), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        } else {
                            text = "—";
                        }
                        containsQr = true;
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj;
                        username = ChatObject.getPublicUsername(chat);
                        usernames.addAll(chat.usernames);
                        String prefix = fragment.getMessagesController().linkPrefix;
                        String suffix = fragment.getTopicId() != 0 ? "/" + fragment.getTopicId() : "";
                        if (ChatObject.isPublic(chat)) {
                            containsQr = true;
                            text = prefix + "/" + username + suffix;
                            value = LocaleController.getString(R.string.InviteLink);
                        } else {
                            text = prefix + "/c/" + chat.id + suffix;
                            value = LocaleController.getString(R.string.InviteLinkPrivate);
                        }
                    } else {
                        text = "";
                        value = "";
                        usernames = new ArrayList<>();
                    }
                    boolean divider = (fragment.getTopicId() != 0 || (!rows.has(Rows.InfoBizHours) && rows.has(Rows.InfoBizLocation))) && !rows.has(Rows.InfoBirthday);
                    detailCell.setTextAndValue(text, alsoUsernamesString(username, usernames, value), divider);
                } else if (kind == Rows.InfoLocation) {
                    TLRPC.ChatFull chatFull = fragment.getMessagesController().getChatFull(fragment.getChatId());
                    if (chatFull != null && chatFull.location instanceof TLRPC.TL_channelLocation) {
                        TLRPC.TL_channelLocation location = (TLRPC.TL_channelLocation) chatFull.location;
                        detailCell.setTextAndValue(location.address, LocaleController.getString(R.string.AttachLocation), false);
                    }
                } else if (kind == Rows.MyPhoneNumber) {
                    TLRPC.User user = UserConfig.getInstance(fragment.getCurrentAccount()).getCurrentUser();
                    String value;
                    if (user != null && user.phone != null && !user.phone.isEmpty()) {
                        value = PhoneFormat.getInstance().format("+" + user.phone);
                    } else {
                        value = LocaleController.getString(R.string.NumberUnknown);
                    }
                    detailCell.setTextAndValue(value, LocaleController.getString(R.string.TapToChangePhone), false);
                    detailCell.setContentDescriptionValueFirst(false);
                } else if (kind == Rows.MyUsername) {
                    TLRPC.User user = UserConfig.getInstance(fragment.getCurrentAccount()).getCurrentUser();
                    String text = "";
                    CharSequence value = LocaleController.getString(R.string.Username);
                    String username = null;
                    if (user != null && !user.usernames.isEmpty()) {
                        for (int i = 0; i < user.usernames.size(); ++i) {
                            TLRPC.TL_username u = user.usernames.get(i);
                            if (u != null && u.active && !TextUtils.isEmpty(u.username)) {
                                username = u.username;
                                break;
                            }
                        }
                        if (username == null) {
                            username = user.username;
                        }
                        if (username == null || TextUtils.isEmpty(username)) {
                            text = LocaleController.getString(R.string.UsernameEmpty);
                        } else {
                            text = "@" + username;
                        }
                        value = alsoUsernamesString(username, user.usernames, value);
                    } else {
                        username = UserObject.getPublicUsername(user);
                        if (user != null && !TextUtils.isEmpty(username)) {
                            text = "@" + username;
                        } else {
                            text = LocaleController.getString(R.string.UsernameEmpty);
                        }
                    }
                    detailCell.setTextAndValue(text, value, false);
                    detailCell.setContentDescriptionValueFirst(true);
                }
                if (containsGift) {
                    Drawable drawable = ContextCompat.getDrawable(detailCell.getContext(), R.drawable.msg_input_gift);
                    drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_switch2TrackChecked), PorterDuff.Mode.MULTIPLY));
                    if (UserObject.areGiftsDisabled(fragment.getUserId())) {
                        detailCell.setImage(null);
                        detailCell.setImageClickListener(null);
                    } else {
                        detailCell.setImage(drawable, LocaleController.getString(R.string.GiftPremium));
                        detailCell.setImageClickListener(v -> fragment.handleDetailCellImageClick(kind));
                    }
                } else if (containsQr) {
                    Drawable drawable = ContextCompat.getDrawable(detailCell.getContext(), R.drawable.msg_qr_mini);
                    drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_switch2TrackChecked), PorterDuff.Mode.MULTIPLY));
                    detailCell.setImage(drawable, LocaleController.getString(R.string.GetQRCode));
                    detailCell.setImageClickListener(v -> fragment.handleDetailCellImageClick(kind));
                } else {
                    detailCell.setImage(null);
                    detailCell.setImageClickListener(null);
                }
                detailCell.textView.setLoading(loadingSpan);
                detailCell.valueTextView.setLoading(loadingSpan);
                break;
            }
            case VIEW_TYPE_ABOUT_LINK: {
                AboutLinkCell aboutLinkCell = (AboutLinkCell) holder.itemView;
                if (kind == Rows.InfoUserAbout) {
                    TLRPC.UserFull userFull = fragment.getMessagesController().getUserFull(fragment.getUserId());
                    TLRPC.User user = rows.payload(kind);
                    boolean addlinks = user != null && (user.bot || (user.premium && userFull.about != null));
                    aboutLinkCell.setTextAndValue(userFull.about, LocaleController.getString(R.string.UserBio), addlinks);
                } else if (kind == Rows.InfoChatAbout) {
                    TLRPC.ChatFull chatFull = fragment.getMessagesController().getChatFull(fragment.getChatId());
                    TLRPC.Chat chat = rows.payload(kind);
                    String text = chatFull.about;
                    while (text.contains("\n\n\n")) {
                        text = text.replace("\n\n\n", "\n\n");
                    }
                    aboutLinkCell.setTextAndValue(text, LocaleController.getString(R.string.ReportChatDescription), ChatObject.isChannel(chat) && !chat.megagroup);
                } else if (kind == Rows.MyBio) {
                    TLRPC.UserFull userFull = fragment.getMessagesController().getUserFull(fragment.getUserId());
                    String value;
                    if (userFull == null || !TextUtils.isEmpty(userFull.about)) {
                        value = userFull == null ? LocaleController.getString(R.string.Loading) : userFull.about;
                        aboutLinkCell.setTextAndValue(value, LocaleController.getString(R.string.UserBio), fragment.getUserConfig().isPremium());
                    } else {
                        aboutLinkCell.setTextAndValue(LocaleController.getString(R.string.UserBio), LocaleController.getString(R.string.UserBioDetail), false);
                    }
                    aboutLinkCell.setMoreButtonDisabled(true);
                }
                break;
            }
            case VIEW_TYPE_SHADOW: {
                ShadowSectionCell sectionCell = (ShadowSectionCell) holder.itemView;
                sectionCell.setTag(pos);
                if (kind == Rows.InfoShadow && !rows.has(Rows.UnblockShadow) && !rows.has(Rows.SecretSettingsShadow) && !rows.has(Rows.SharedMedia) && !rows.has(Rows.MembersShadow)
                                || kind == Rows.SecretSettingsShadow
                                || kind == Rows.UnblockShadow
                                || kind == Rows.SharedMediaPrefix
                                || kind == Rows.MembersShadow && !rows.has(Rows.UnblockShadow) && !rows.has(Rows.SharedMedia)) {
                    sectionCell.setTopBottom(true, false);
                } else {
                    sectionCell.setTopBottom(true, true);
                }
                break;
            }
            case VIEW_TYPE_SUGGESTION: {
                SettingsSuggestionCell suggestionCell = (SettingsSuggestionCell) holder.itemView;
                if (kind == Rows.SuggestionPassword) {
                    suggestionCell.setType(SettingsSuggestionCell.TYPE_PASSWORD);
                } else if (kind == Rows.SuggestionPhone) {
                    suggestionCell.setType(SettingsSuggestionCell.TYPE_PHONE);
                } else if (kind == Rows.SuggestionGrace) {
                    suggestionCell.setType(SettingsSuggestionCell.TYPE_GRACE);
                }
                break;
            }
            /* Moved to header case VIEW_TYPE_NOTIFICATIONS_CHECK_SIMPLE: {
                TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                textCheckCell.setTextAndCheck(LocaleController.getString(R.string.Notifications), !fragment.getMessagesController().isDialogMuted(fragment.getDialogId(), fragment.getTopicId()), false);
                break;
            } */
            case VIEW_TYPE_LOCATION: {
                ProfileLocationCell locationCell = (ProfileLocationCell) holder.itemView;
                TLRPC.TL_businessLocation location = rows.payload(kind);
                locationCell.set(location, !fragment.isMyProfile());
                break;
            }
            case VIEW_TYPE_COLORFUL_TEXT: {
                TLRPC.UserFull userInfo = fragment.getMessagesController().getUserFull(fragment.getUserId());
                AffiliateProgramFragment.ColorfulTextCell cell = (AffiliateProgramFragment.ColorfulTextCell) holder.itemView;
                cell.set(getThemedColor(Theme.key_color_green), R.drawable.filled_affiliate, LocaleController.getString(R.string.ProfileBotAffiliateProgram), null);
                cell.setPercent(userInfo != null && userInfo.starref_program != null ? AffiliateProgramFragment.percents(userInfo.starref_program.commission_permille) : null);
                break;
            }
            case VIEW_TYPE_CHANNEL: {
                if (kind == Rows.PersonalChannel) {
                    TLRPC.UserFull userInfo = fragment.getMessagesController().getUserFull(fragment.getUserId());
                    MessageObject messageObject = rows.payload(kind);
                    ProfileChannelCell channelCell = (ProfileChannelCell) holder.itemView;
                    channelCell.set(fragment.getMessagesController().getChat(userInfo.personal_channel_id), messageObject);
                }
                break;
            }
            case VIEW_TYPE_HOURS: {
                if (kind == Rows.InfoBizHours) {
                    TL_account.TL_businessWorkHours workHours = rows.payload(kind);
                    ProfileHoursCell hoursCell = (ProfileHoursCell) holder.itemView;
                    boolean divider = rows.has(Rows.InfoBizLocation);
                    hoursCell.setOnTimezoneSwitchClick(view -> hoursCell.set(workHours, true, !hoursCell.isShowInMyTimezone(), divider));
                    hoursCell.set(workHours, hoursCell.isExpanded(), hoursCell.isShowInMyTimezone(), divider);
                }
                break;
            }
            case VIEW_TYPE_SHADOW_TEXT: {
                TLRPC.UserFull userInfo = fragment.getMessagesController().getUserFull(fragment.getUserId());
                TLRPC.ChatFull chatInfo = fragment.getMessagesController().getChatFull(fragment.getChatId());
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                Context context = cell.getContext();
                cell.setLinkTextRippleColor(null);
                CharSequence text = null;
                int fixedSize = 0;
                if (kind == Rows.ActionsAddToGroupInfo) {
                    text = LocaleController.getString(R.string.BotAddToGroupOrChannelInfo);
                } else if (kind == Rows.InfoShadow) {
                    TL_bots.botVerification bot_verification = userInfo != null ? userInfo.bot_verification : chatInfo != null ? chatInfo.bot_verification : null;
                    if (rows.has(Rows.InfoBotApp) || bot_verification != null) {
                        final TLRPC.User user = rows.payload(kind);
                        final boolean botOwner = user != null && user.bot && user.bot_can_edit;
                        SpannableStringBuilder sb = new SpannableStringBuilder();

                        if (rows.has(Rows.InfoBotApp)) {
                            sb.append(AndroidUtilities.replaceSingleTag(LocaleController.getString(botOwner ? R.string.ProfileBotOpenAppInfoOwner : R.string.ProfileBotOpenAppInfo), () -> {
                                Browser.openUrl(context, LocaleController.getString(botOwner ? R.string.ProfileBotOpenAppInfoOwnerLink : R.string.ProfileBotOpenAppInfoLink));
                            }));
                            if (bot_verification != null) {
                                sb.append("\n\n\n");
                            }
                        }
                        if (bot_verification != null) {
                            sb.append("x");
                            sb.setSpan(new AnimatedEmojiSpan(bot_verification.icon, cell.getTextView().getPaint().getFontMetricsInt()), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            sb.append(" ");
                            SpannableString description = new SpannableString(bot_verification.description);
                            try {
                                AndroidUtilities.addLinksSafe(description, Linkify.WEB_URLS, false, false);
                                URLSpan[] spans = description.getSpans(0, description.length(), URLSpan.class);
                                for (int i = 0; i < spans.length; ++i) {
                                    URLSpan span = spans[i];
                                    int start = description.getSpanStart(span);
                                    int end = description.getSpanEnd(span);
                                    final String url = span.getURL();

                                    description.removeSpan(span);
                                    description.setSpan(new URLSpan(url) {
                                        @Override
                                        public void onClick(View widget) {
                                            Browser.openUrl(context, url);
                                        }

                                        @Override
                                        public void updateDrawState(@NonNull TextPaint ds) {
                                            ds.setUnderlineText(true);
                                        }
                                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            sb.append(description);
                        }

                        cell.setLinkTextRippleColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteGrayText4), 0.2f));
                        text = sb;
                    } else {
                        fixedSize = 14;
                    }
                } else if (kind == Rows.AffiliateInfo) {
                    final TLRPC.User botUser = rows.payload(kind);
                    if (botUser != null && botUser.bot && botUser.bot_can_edit) {
                        text = LocaleController.formatString(R.string.ProfileBotAffiliateProgramInfoOwner, UserObject.getUserName(botUser), AffiliateProgramFragment.percents(userInfo != null && userInfo.starref_program != null ? userInfo.starref_program.commission_permille : 0));
                    } else {
                        text = LocaleController.formatString(R.string.ProfileBotAffiliateProgramInfo, UserObject.getUserName(botUser), AffiliateProgramFragment.percents(userInfo != null && userInfo.starref_program != null ? userInfo.starref_program.commission_permille : 0));
                    }
                }
                cell.setFixedSize(fixedSize);
                cell.setText(text);

                int drawable;
                if (kind == Rows.InfoShadow && !rows.has(Rows.UnblockShadow) && !rows.has(Rows.SecretSettingsShadow) && !rows.has(Rows.SharedMedia) && !rows.has(Rows.MembersShadow)
                        || kind == Rows.SecretSettingsShadow
                        || kind == Rows.UnblockShadow
                        || kind == Rows.ActionsAddToGroupInfo && rows.kind(pos + 1) == Rows.Filler
                        || kind == Rows.MembersShadow && !rows.has(Rows.UnblockShadow) && !rows.has(Rows.SharedMedia)) {
                    drawable = R.drawable.greydivider_bottom;
                } else {
                    drawable = R.drawable.greydivider;
                }
                Drawable shadow = Theme.getThemedDrawable(context, drawable, getThemedColor(Theme.key_windowBackgroundGrayShadow));
                Drawable background = new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray));
                CombinedDrawable combined = new CombinedDrawable(background, shadow, 0, 0);
                combined.setFullsize(true);
                cell.setBackground(combined);
                break;
            }
            /* case VIEW_TYPE_NOTIFICATIONS_CHECK: {
                NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                if (kind == Rows.Notifications) {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(fragment.getCurrentAccount());
                    long did = fragment.getDialogId();
                    String key = NotificationsController.getSharedPrefKey(did, fragment.getTopicId());
                    boolean enabled = false;
                    boolean custom = preferences.getBoolean("custom_" + key, false);
                    boolean hasOverride = preferences.contains("notify2_" + key);
                    int value = preferences.getInt("notify2_" + key, 0);
                    int delta = preferences.getInt("notifyuntil_" + key, 0);
                    String val;
                    if (value == 3 && delta != Integer.MAX_VALUE) {
                        delta -= fragment.getConnectionsManager().getCurrentTime();
                        if (delta <= 0) {
                            val = LocaleController.getString(custom ? R.string.NotificationsCustom : R.string.NotificationsOn);
                            enabled = true;
                        } else if (delta < 60 * 60) {
                            val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Minutes", delta / 60));
                        } else if (delta < 60 * 60 * 24) {
                            val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Hours", (int) Math.ceil(delta / 60.0f / 60)));
                        } else if (delta < 60 * 60 * 24 * 365) {
                            val = LocaleController.formatString("WillUnmuteIn", R.string.WillUnmuteIn, LocaleController.formatPluralString("Days", (int) Math.ceil(delta / 60.0f / 60 / 24)));
                        } else {
                            val = null;
                        }
                    } else {
                        if (value == 0) {
                            enabled = hasOverride || NotificationsController.getInstance(fragment.getCurrentAccount()).isGlobalNotificationsEnabled(did, false, false);
                        } else if (value == 1) {
                            enabled = true;
                        }
                        val = LocaleController.getString(enabled && custom ? R.string.NotificationsCustom : enabled ? R.string.NotificationsOn : R.string.NotificationsOff);
                    }
                    if (val == null) {
                        val = LocaleController.getString(R.string.NotificationsOff);
                    }
                    if (!fragment.notificationsExceptions.isEmpty()) {
                        val = String.format(Locale.US, LocaleController.getPluralString("NotificationTopicExceptionsDesctription", fragment.notificationsExceptions.size()), val, fragment.notificationsExceptions.size());
                    }
                    checkCell.setAnimationsEnabled(true);
                    checkCell.setTextAndValueAndCheck(LocaleController.getString(R.string.Notifications), val, enabled, rows.has(Rows.BotApp));
                }
                break;
            } */
            case VIEW_TYPE_PREMIUM_TEXT_CELL:
            case VIEW_TYPE_STARS_TEXT_CELL:
            case VIEW_TYPE_TEXT: {
                TextCell textCell = (TextCell) holder.itemView;
                textCell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                if (kind == Rows.SecretSettingsTimer) {
                    String value;
                    if (fragment.chatEncrypted.ttl == 0) {
                        value = LocaleController.getString(R.string.ShortMessageLifetimeForever);
                    } else {
                        value = LocaleController.formatTTLString(fragment.chatEncrypted.ttl);
                    }
                    textCell.setTextAndValue(LocaleController.getString(R.string.MessageLifetime), value, false, false);
                } else if (kind == Rows.Unblock) {
                    textCell.setText(LocaleController.getString(R.string.Unblock), false);
                    textCell.setColors(-1, Theme.key_text_RedRegular);
                } else if (kind == Rows.SecretSettingsKey) {
                    IdenticonDrawable identiconDrawable = new IdenticonDrawable();
                    identiconDrawable.setEncryptedChat(fragment.chatEncrypted);
                    textCell.setTextAndValueDrawable(LocaleController.getString(R.string.EncryptionKey), identiconDrawable, false);
                } /* WIP: Moved to header else if (kind == Rows.Join) {
                    textCell.setColors(-1, Theme.key_windowBackgroundWhiteBlueText2);
                    if (fragment.getCurrentChat() != null && fragment.getCurrentChat().megagroup) {
                        textCell.setText(LocaleController.getString(R.string.ProfileJoinGroup), false);
                    } else {
                        textCell.setText(LocaleController.getString(R.string.ProfileJoinChannel), false);
                    }
                } */ else if (kind == Rows.ChannelOptionsSubscribers) {
                    String text = LocaleController.getString(R.string.ChannelSubscribers);
                    boolean divider = pos != rows.position(Rows.MembersShadow) - 1;
                    TLRPC.ChatFull chatInfo = fragment.getMessagesController().getChatFull(fragment.getChatId());
                    if (chatInfo != null) {
                        textCell.setTextAndValueAndIcon(text, LocaleController.formatNumber(chatInfo.participants_count, ','), R.drawable.msg_groups, divider);
                    } else {
                        textCell.setTextAndIcon(text, R.drawable.msg_groups, divider);
                    }
                } else if (kind == Rows.ChannelOptionsSubscribersRequests) {
                    boolean divider = pos != rows.position(Rows.MembersShadow) - 1;
                    TLRPC.ChatFull chatInfo = fragment.getMessagesController().getChatFull(fragment.getChatId());
                    if (chatInfo != null) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.SubscribeRequests), String.format("%d", chatInfo.requests_pending), R.drawable.msg_requests, divider);
                    }
                } else if (kind == Rows.ChannelOptionsAdministrators) {
                    boolean divider = pos != rows.position(Rows.MembersShadow) - 1;
                    TLRPC.ChatFull chatInfo = fragment.getMessagesController().getChatFull(fragment.getChatId());
                    if (chatInfo != null) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.ChannelAdministrators), String.format("%d", chatInfo.admins_count), R.drawable.msg_admins, divider);
                    } else {
                        textCell.setTextAndIcon(LocaleController.getString(R.string.ChannelAdministrators), R.drawable.msg_admins, divider);
                    }
                } else if (kind == Rows.ChannelOptionsSettings) {
                    boolean divider = pos != rows.position(Rows.MembersShadow) - 1;
                    textCell.setTextAndIcon(LocaleController.getString(R.string.ChannelAdminSettings), R.drawable.msg_customize, divider);
                } else if (kind == Rows.ChannelOptionsBalance) {
                    final BotStarsController controller = BotStarsController.getInstance(fragment.getCurrentAccount());
                    final TL_stars.StarsAmount stars_balance = controller.getBotStarsBalance(-fragment.getChatId());
                    final long ton_balance = controller.getTONBalance(-fragment.getChatId());
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    if (ton_balance > 0) {
                        if (ton_balance / 1_000_000_000.0 > 1000.0) {
                            ssb.append("TON ").append(AndroidUtilities.formatWholeNumber((int) (ton_balance / 1_000_000_000.0), 0));
                        } else {
                            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
                            symbols.setDecimalSeparator('.');
                            DecimalFormat formatterTON = new DecimalFormat("#.##", symbols);
                            formatterTON.setMinimumFractionDigits(2);
                            formatterTON.setMaximumFractionDigits(3);
                            formatterTON.setGroupingUsed(false);
                            ssb.append("TON ").append(formatterTON.format(ton_balance / 1_000_000_000.0));
                        }
                    }
                    if (stars_balance.amount > 0) {
                        if (ssb.length() > 0) ssb.append(" ");
                        ssb.append("XTR ").append(StarsIntroActivity.formatStarsAmountShort(stars_balance));
                    }
                    textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.ChannelStars), ChannelMonetizationLayout.replaceTON(StarsIntroActivity.replaceStarsWithPlain(ssb, .7f), textCell.getTextView().getPaint()), R.drawable.menu_feature_paid, true);
                } else if (kind == Rows.ActionsBotStarsBalance) {
                    final BotStarsController controller = BotStarsController.getInstance(fragment.getCurrentAccount());
                    final TL_stars.StarsAmount stars_balance = controller.getBotStarsBalance(fragment.getUserId());
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    if (stars_balance.amount > 0) {
                        ssb.append("XTR ").append(StarsIntroActivity.formatStarsAmountShort(stars_balance));
                    }
                    textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.BotBalanceStars), ChannelMonetizationLayout.replaceTON(StarsIntroActivity.replaceStarsWithPlain(ssb, .7f), textCell.getTextView().getPaint()), R.drawable.menu_premium_main, true);
                } else if (kind == Rows.ActionsBotTonBalance) {
                    final BotStarsController controller = BotStarsController.getInstance(fragment.getCurrentAccount());
                    long ton_balance = controller.getTONBalance(fragment.getUserId());
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    if (ton_balance > 0) {
                        if (ton_balance / 1_000_000_000.0 > 1000.0) {
                            ssb.append("TON ").append(AndroidUtilities.formatWholeNumber((int) (ton_balance / 1_000_000_000.0), 0));
                        } else {
                            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
                            symbols.setDecimalSeparator('.');
                            DecimalFormat formatterTON = new DecimalFormat("#.##", symbols);
                            formatterTON.setMinimumFractionDigits(2);
                            formatterTON.setMaximumFractionDigits(3);
                            formatterTON.setGroupingUsed(false);
                            ssb.append("TON ").append(formatterTON.format(ton_balance / 1_000_000_000.0));
                        }
                    }
                    textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.BotBalanceTON), ChannelMonetizationLayout.replaceTON(StarsIntroActivity.replaceStarsWithPlain(ssb, .7f), textCell.getTextView().getPaint()), R.drawable.msg_ton, true);
                } else if (kind == Rows.ChannelOptionsBlockedUsers) {
                    boolean divider = pos != rows.position(Rows.MembersShadow) - 1;
                    TLRPC.ChatFull chatInfo = fragment.getMessagesController().getChatFull(fragment.getChatId());
                    if (chatInfo != null) {
                        textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.ChannelBlacklist), String.format("%d", Math.max(chatInfo.banned_count, chatInfo.kicked_count)), R.drawable.msg_user_remove, divider);
                    } else {
                        textCell.setTextAndIcon(LocaleController.getString(R.string.ChannelBlacklist), R.drawable.msg_user_remove, divider);
                    }
                } else if (kind == Rows.MembersAdd) {
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    boolean isNextPositionMember = pos + 1 == rows.position(Rows.Members);
                    textCell.setTextAndIcon(LocaleController.getString(R.string.AddMember), R.drawable.msg_contact_add, !rows.has(Rows.MembersShadow) || isNextPositionMember);
                } /*else if (kind == Rows.SendMessage) {
                    textCell.setText(LocaleController.getString(R.string.SendMessageLocation), true);
                }*/ else if (kind == Rows.ActionsAddToContacts) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.AddToContacts), R.drawable.msg_contact_add, false);
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                } else if (kind == Rows.ActionsReportReaction) {
                    Long fromDialogId = rows.payload(kind);
                    TLRPC.Chat chat = fragment.getMessagesController().getChat(fromDialogId == null ? 0 : -fromDialogId);
                    if (chat != null && ChatObject.canBlockUsers(chat)) {
                        textCell.setTextAndIcon(LocaleController.getString(R.string.ReportReactionAndBan), R.drawable.msg_block2, false);
                    } else {
                        textCell.setTextAndIcon(LocaleController.getString(R.string.ReportReaction), R.drawable.msg_report, false);
                    }
                    textCell.setColors(Theme.key_text_RedBold, Theme.key_text_RedRegular);
                    textCell.setColors(Theme.key_text_RedBold, Theme.key_text_RedRegular);
                } else /* WIP: Moved to header if (kind == Rows.Report) {
                    textCell.setText(LocaleController.getString(R.string.ReportUserLocation), false);
                    textCell.setColors(-1, Theme.key_text_RedRegular);
                    textCell.setColors(-1, Theme.key_text_RedRegular);
                } else*/ if (kind == Rows.MySettingsLanguage) {
                    textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.Language), LocaleController.getCurrentLanguageName(), false, R.drawable.msg2_language, false);
                    textCell.setImageLeft(23);
                } else if (kind == Rows.MySettingsNotification) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.NotificationsAndSounds), R.drawable.msg2_notifications, true);
                } else if (kind == Rows.MySettingsPrivacy) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.PrivacySettings), R.drawable.msg2_secret, true);
                } else if (kind == Rows.MySettingsData) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.DataSettings), R.drawable.msg2_data, true);
                } else if (kind == Rows.MySettingsChat) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.ChatSettings), R.drawable.msg2_discussion, true);
                } else if (kind == Rows.MySettingsFilters) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.Filters), R.drawable.msg2_folder, true);
                } else if (kind == Rows.MySettingsLiteMode) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.PowerUsage), R.drawable.msg2_battery, true);
                } else if (kind == Rows.HelpQuestion) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.AskAQuestion), R.drawable.msg2_ask_question, true);
                } else if (kind == Rows.HelpFaq) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.TelegramFAQ), R.drawable.msg2_help, true);
                } else if (kind == Rows.HelpPolicy) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.PrivacyPolicy), R.drawable.msg2_policy, false);
                } else if (kind == Rows.DebugSendLogs) {
                    textCell.setText(LocaleController.getString(R.string.DebugSendLogs), true);
                } else if (kind == Rows.DebugSendLastLogs) {
                    textCell.setText(LocaleController.getString(R.string.DebugSendLastLogs), true);
                } else if (kind == Rows.DebugClearLogs) {
                    textCell.setText(LocaleController.getString(R.string.DebugClearLogs), rows.has(Rows.DebugSwitchBackend));
                } else if (kind == Rows.DebugSwitchBackend) {
                    textCell.setText("Switch Backend", false);
                } else if (kind == Rows.MySettingsDevices) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.Devices), R.drawable.msg2_devices, true);
                } else if (kind == Rows.ActionsAddToGroupButton) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.AddToGroupOrChannel), R.drawable.msg_groups_create, false);
                } else if (kind == Rows.FeaturesPremium) {
                    Drawable drawable =  new AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, dp(24), dp(24));
                    textCell.setTextAndIcon(LocaleController.getString(R.string.TelegramPremium), drawable, true);
                    textCell.setImageLeft(23);
                } else if (kind == Rows.FeaturesStars) {
                    StarsController c = StarsController.getInstance(fragment.getCurrentAccount());
                    long balance = c.getBalance().amount;
                    Drawable drawable =  new AnimatedEmojiDrawable.WrapSizeDrawable(PremiumGradient.getInstance().premiumStarMenuDrawable, dp(24), dp(24));
                    textCell.setTextAndValueAndIcon(LocaleController.getString(R.string.MenuTelegramStars), c.balanceAvailable() && balance > 0 ? LocaleController.formatNumber((int) balance, ',') : "", drawable, true);
                    textCell.setImageLeft(23);
                } else if (kind == Rows.FeaturesBusiness) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.TelegramBusiness), R.drawable.menu_shop, true);
                    textCell.setImageLeft(23);
                } else if (kind == Rows.FeaturesGift) {
                    textCell.setTextAndIcon(LocaleController.getString(R.string.SendAGift), R.drawable.menu_gift, false);
                    textCell.setImageLeft(23);
                } else if (kind == Rows.BotPermissionLocation) {
                    BotLocation botLocation = rows.payload(Rows.BotPermissionLocation);
                    String text = LocaleController.getString(R.string.BotProfilePermissionLocation);
                    textCell.setTextAndCheckAndColorfulIcon(text, botLocation != null && botLocation.granted(), R.drawable.filled_access_location, getThemedColor(Theme.key_color_green), rows.has(Rows.BotPermissionBiometry));
                } else if (kind == Rows.BotPermissionBiometry) {
                    BotBiometry botBiometry = rows.payload(Rows.BotPermissionBiometry);
                    String text = LocaleController.getString(R.string.BotProfilePermissionBiometry);
                    textCell.setTextAndCheckAndColorfulIcon(text, botBiometry != null && botBiometry.granted(), R.drawable.filled_access_fingerprint, getThemedColor(Theme.key_color_orange), false);
                } else if (kind == Rows.BotPermissionEmojiStatus) {
                    TLRPC.UserFull userInfo = fragment.getMessagesController().getUserFull(fragment.getUserId());
                    String text = LocaleController.getString(R.string.BotProfilePermissionEmojiStatus);
                    textCell.setTextAndCheckAndColorfulIcon(text, userInfo != null && userInfo.bot_can_manage_emoji_status, R.drawable.filled_access_sleeping, getThemedColor(Theme.key_color_lightblue), rows.has(Rows.BotPermissionLocation) || rows.has(Rows.BotPermissionBiometry));
                }
                textCell.valueTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
                break;
            }
            case VIEW_TYPE_USER:
                List<TLRPC.ChatParticipant> participants = rows.payload(Rows.Members);
                UserCell userCell = (UserCell) holder.itemView;
                TLRPC.ChatParticipant part;
                try {
                    part = participants.get(pos - rows.position(Rows.Members));
                } catch (Exception e) {
                    part = null;
                    FileLog.e(e);
                }
                if (part != null) {
                    String role;
                    if (part instanceof TLRPC.TL_chatChannelParticipant) {
                        TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                        if (!TextUtils.isEmpty(channelParticipant.rank)) {
                            role = channelParticipant.rank;
                        } else {
                            if (channelParticipant instanceof TLRPC.TL_channelParticipantCreator) {
                                role = LocaleController.getString(R.string.ChannelCreator);
                            } else if (channelParticipant instanceof TLRPC.TL_channelParticipantAdmin) {
                                role = LocaleController.getString(R.string.ChannelAdmin);
                            } else {
                                role = null;
                            }
                        }
                    } else {
                        if (part instanceof TLRPC.TL_chatParticipantCreator) {
                            role = LocaleController.getString(R.string.ChannelCreator);
                        } else if (part instanceof TLRPC.TL_chatParticipantAdmin) {
                            role = LocaleController.getString(R.string.ChannelAdmin);
                        } else {
                            role = null;
                        }
                    }
                    boolean divider = pos != rows.position(Rows.Members) + participants.size() - 1;
                    userCell.setAdminRole(role);
                    userCell.setData(fragment.getMessagesController().getUser(part.user_id), null, null, 0, divider);
                }
                break;
        }
    }
}
