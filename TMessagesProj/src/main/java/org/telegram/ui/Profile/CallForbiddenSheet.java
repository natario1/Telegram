package org.telegram.ui.Profile;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.Components.*;

import java.util.function.Consumer;

import static org.telegram.messenger.AndroidUtilities.dp;

public class CallForbiddenSheet extends BottomSheet {

    private Consumer<Boolean> onClick;
    private final TLRPC.User user;

    public CallForbiddenSheet(Context context, TLRPC.User user, Consumer<Boolean> onClick) {
        super(context, false);
        this.onClick = onClick;
        this.user = user;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        int imagePadding = dp(84 - 42) / 2;
        FrameLayout block = new FrameLayout(context);
        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setPadding(imagePadding, imagePadding, imagePadding, imagePadding);
        imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.story_link));
        imageView.setBackground(Theme.createCircleDrawable(dp(84), Theme.getColor(Theme.key_featuredStickers_addButton)));
        block.addView(imageView, LayoutHelper.createFrame(84, 84, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        linearLayout.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 84+20));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(LocaleController.getString(R.string.CallInviteViaLinkTitle));
        textView.setPadding(dp(30), dp(12), dp(30), 0);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.8F);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.InviteCallRestrictedUsersOne, ContactsController.formatName(user))));
        textView.setPadding(dp(30), dp(6), dp(30), dp(11));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(linearLayout);
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        textView = new TextView(context);
        textView.setText(LocaleController.getString(R.string.SendInviteLink));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        textView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 8));
        textView.setOnClickListener(e -> generateLink());
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 14, 14, 10));
    }

    private void generateLink() {
        CallLogActivity.createCallLink(getContext(), currentAccount, null, false, (link) -> {
            if (link != null) {
                final SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(link, user.id, null, null, null, true, null, null, null, false, 0, null, false);
                SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                if (this.onClick != null) {
                    this.onClick.accept(true);
                    this.onClick = null;
                }
                dismiss();
                BulletinFactory factory = BulletinFactory.global();
                factory.createSimpleBulletin(R.raw.voip_invite, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.InviteLinkSentSingle, ContactsController.formatName(user)))).show();
            } else {
                dismiss();
            }
        });
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (onClick != null) {
            onClick.accept(false);
            onClick = null;
        }
    }
}
