package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
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
    public ProfileGiftsView giftsView;

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
        this(args, null);
    }

    public ProfileActivityReplacement(Bundle args, SharedMediaLayout.SharedMediaPreloader preloader) {
        super(args);
        sharedMediaPreloader = preloader;
    }

    public int birthdayRow;
    public RecyclerListView getListView() {
        throw new RuntimeException("TODO: not implemented");
    }
    public boolean myProfile;
    public boolean saved;

    public UndoView getUndoView() {
        throw new RuntimeException("TODO: not implemented");
    }

    public void setPlayProfileAnimation(int type) {

    }

    public void scrollToSharedMedia() {

    }

    public void scrollToSharedMedia(boolean animated) {

    }

    public void prepareBlurBitmap() {

    }

    public boolean isChat() {
        throw new RuntimeException("TODO: not implemented");
    }

    public long getDialogId() {
        throw new RuntimeException("TODO: not implemented");
        /* if (dialogId != 0) {
            return dialogId;
        } else if (userId != 0) {
            return userId;
        } else {
            return -chatId;
        } */
    }

    public void setUserInfo(
            TLRPC.UserFull value,
            ProfileChannelCell.ChannelMessageFetcher channelMessageFetcher,
            ProfileBirthdayEffect.BirthdayEffectFetcher birthdayAssetsFetcher
    ) {

    }

    public TLRPC.UserFull getUserInfo() {
        throw new RuntimeException("TODO: not implemented");
    }

    public TLRPC.Chat getCurrentChat() {
        throw new RuntimeException("TODO: not implemented");
    }

    public void setChatInfo(TLRPC.ChatFull value) {

    }

    public long getTopicId() {
        throw new RuntimeException("TODO: not implemented");
    }

    public boolean isSettings() {
        throw new RuntimeException("TODO: not implemented");
        // return imageUpdater != null && !myProfile;
    }
}
