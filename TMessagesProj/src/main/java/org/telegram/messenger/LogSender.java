package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.telegram.ui.ActionBar.AlertDialog;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Migrated from old ProfileActivity
public class LogSender {
    public static void sendLogs(Activity activity, boolean last) {
        if (activity == null) {
            return;
        }
        AlertDialog progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File dir = AndroidUtilities.getLogsDir();
                if (dir == null) {
                    AndroidUtilities.runOnUIThread(progressDialog::dismiss);
                    return;
                }

                File zipFile = new File(dir, "logs.zip");
                if (zipFile.exists()) {
                    zipFile.delete();
                }

                ArrayList<File> files = new ArrayList<>();

                File[] logFiles = dir.listFiles();
                for (File f : logFiles) {
                    files.add(f);
                }

                File filesDir = ApplicationLoader.getFilesDirFixed();
                filesDir = new File(filesDir, "malformed_database/");
                if (filesDir.exists() && filesDir.isDirectory()) {
                    File[] malformedDatabaseFiles = filesDir.listFiles();
                    for (File file : malformedDatabaseFiles) {
                        files.add(file);
                    }
                }

                boolean[] finished = new boolean[1];
                long currentDate = System.currentTimeMillis();

                BufferedInputStream origin = null;
                ZipOutputStream out = null;
                try {
                    FileOutputStream dest = new FileOutputStream(zipFile);
                    out = new ZipOutputStream(new BufferedOutputStream(dest));
                    byte[] data = new byte[1024 * 64];

                    for (int i = 0; i < files.size(); i++) {
                        File file = files.get(i);
                        if (!file.getName().contains("cache4") && (last || file.getName().contains("_mtproto")) && (currentDate - file.lastModified()) > 24 * 60 * 60 * 1000) {
                            continue;
                        }
                        if (!file.exists()) {
                            continue;
                        }
                        FileInputStream fi = new FileInputStream(file);
                        origin = new BufferedInputStream(fi, data.length);

                        ZipEntry entry = new ZipEntry(file.getName());
                        out.putNextEntry(entry);
                        int count;
                        while ((count = origin.read(data, 0, data.length)) != -1) {
                            out.write(data, 0, count);
                        }
                        origin.close();
                        origin = null;
                    }
                    finished[0] = true;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (origin != null) {
                        origin.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignore) {

                    }
                    if (finished[0]) {
                        Uri uri;
                        if (Build.VERSION.SDK_INT >= 24) {
                            uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", zipFile);
                        } else {
                            uri = Uri.fromFile(zipFile);
                        }

                        Intent i = new Intent(Intent.ACTION_SEND);
                        if (Build.VERSION.SDK_INT >= 24) {
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        i.setType("message/rfc822");
                        i.putExtra(Intent.EXTRA_EMAIL, "");
                        i.putExtra(Intent.EXTRA_SUBJECT, "Logs from " + LocaleController.getInstance().getFormatterStats().format(System.currentTimeMillis()));
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        try {
                            activity.startActivityForResult(Intent.createChooser(i, "Select email application."), 500);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        Toast.makeText(activity, LocaleController.getString(R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}

