package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ProfileChannelCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Stars.ProfileGiftsView;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SuppressWarnings("name")
public class ProfileActivityReplacement extends BaseFragment {

    private SharedMediaLayout.SharedMediaPreloader sharedMediaPreloader;
    public SharedMediaLayout sharedMediaLayout;

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

    // Called before presenting
    public void setSharedMediaPreloader(SharedMediaLayout.SharedMediaPreloader preloader) {
        sharedMediaPreloader = preloader;
    }

    // Called before presenting
    public void setPlayProfileAnimation(int type) {

    }

    // Called before presenting
    public void setUserInfo(
        TLRPC.UserFull value,
        ProfileChannelCell.ChannelMessageFetcher channelMessageFetcher,
        ProfileBirthdayEffect.BirthdayEffectFetcher birthdayAssetsFetcher
    ) {

    }

    public TLRPC.User getCurrentUser() {
        throw new RuntimeException("TODO: not implemented");
    }

    // Called before presenting
    public void setChatInfo(TLRPC.ChatFull value) {

    }

    public TLRPC.Chat getCurrentChat() {
        throw new RuntimeException("TODO: not implemented");
    }

    @Nullable
    public UndoView getUndoView() {
        throw new RuntimeException("TODO: not implemented");
    }

    public long getDialogId() {
        throw new RuntimeException("TODO: not implemented");
    }

    public long getTopicId() {
        throw new RuntimeException("TODO: not implemented");
    }

    public boolean isChat() {
        throw new RuntimeException("TODO: not implemented");
    }

    public boolean isSettings() {
        throw new RuntimeException("TODO: not implemented");
        // return imageUpdater != null && !myProfile;
    }

    public boolean isMyProfile() {
        throw new RuntimeException("TODO: not implemented");
    }

    public boolean isSaved() {
        throw new RuntimeException("TODO: not implemented");
    }

    public void updateGifts() {
        // giftsView.update()
    }

    public void scrollToSharedMedia(boolean animated) {

    }

    public void prepareBlurBitmap() {

    }
}
