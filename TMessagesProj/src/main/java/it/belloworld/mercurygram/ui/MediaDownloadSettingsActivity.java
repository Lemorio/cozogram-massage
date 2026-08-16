package it.belloworld.mercurygram.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Media & Downloads settings skeleton. Fase A owns only the global mode and
 * preference model. Media renderers, variant resolution, album tasks, and
 * cleanup are intentionally deferred to later phases.
 */
public class MediaDownloadSettingsActivity extends UniversalFragment {

    private static final int ID_DOWNLOAD_MODE = 1;
    private static final int ID_VIDEO_QUALITY = 2;
    private static final int ID_PHOTO_QUALITY = 3;
    private static final int ID_AUDIO_POLICY = 4;
    private static final int ID_GIF_POLICY = 5;
    private static final int ID_FILE_LIMIT = 6;
    private static final int ID_ASK_LARGE = 7;
    private static final int ID_NETWORK_PROFILES = 8;
    private static final int ID_ALBUMS = 9;
    private static final int ID_STORAGE = 10;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MediaDownloads);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MediaDownloadsDownloadMode)));
        items.add(UItem.asButton(ID_DOWNLOAD_MODE,
                LocaleController.getString(R.string.MediaDownloadsDownloadMode),
                downloadModeLabel()));
        items.add(UItem.asShadow(downloadModeAbout()));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MediaDownloads)));
        addDeferredRow(items, ID_VIDEO_QUALITY, R.string.MediaDownloadsVideoQuality);
        addDeferredRow(items, ID_PHOTO_QUALITY, R.string.MediaDownloadsPhotoQuality);
        addDeferredRow(items, ID_AUDIO_POLICY, R.string.MediaDownloadsAudioPolicy);
        addDeferredRow(items, ID_GIF_POLICY, R.string.MediaDownloadsGifPolicy);
        addDeferredRow(items, ID_FILE_LIMIT, R.string.MediaDownloadsFileLimit);
        addDeferredRow(items, ID_ASK_LARGE, R.string.MediaDownloadsAskLarge);
        addDeferredRow(items, ID_NETWORK_PROFILES, R.string.MediaDownloadsNetworkProfiles);
        addDeferredRow(items, ID_ALBUMS, R.string.MediaDownloadsAlbums);
        addDeferredRow(items, ID_STORAGE, R.string.MediaDownloadsStorage);
        items.add(UItem.asShadow(LocaleController.getString(R.string.MediaDownloadsAbout)));
    }

    private void addDeferredRow(ArrayList<UItem> items, int id, int titleRes) {
        // Phase A deliberately exposes the future sections as disabled skeleton
        // rows; no fake quality/size choices are presented before server-variant
        // resolution is implemented in later phases.
        items.add(UItem.asButton(id, LocaleController.getString(titleRes),
                LocaleController.getString(R.string.MediaDownloadsPhaseAPlaceholder)).setEnabled(false));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_DOWNLOAD_MODE) {
            showDownloadModeDialog();
        }
    }

    private String downloadModeLabel() {
        switch (getUserConfig().mg.customMediaDownloadMode) {
            case 1:
                return LocaleController.getString(R.string.MediaDownloadsModeSelectedQuality);
            case 2:
                return LocaleController.getString(R.string.MediaDownloadsModeOriginal);
            case 3:
                return LocaleController.getString(R.string.MediaDownloadsModeDataSaver);
            case 4:
                return LocaleController.getString(R.string.MediaDownloadsModeManualOnly);
            default:
                return LocaleController.getString(R.string.MediaDownloadsModeSmartAsk);
        }
    }

    private String downloadModeAbout() {
        switch (getUserConfig().mg.customMediaDownloadMode) {
            case 1:
                return LocaleController.getString(R.string.MediaDownloadsModeSelectedQualityAbout);
            case 2:
                return LocaleController.getString(R.string.MediaDownloadsModeOriginalAbout);
            case 3:
                return LocaleController.getString(R.string.MediaDownloadsModeDataSaverAbout);
            case 4:
                return LocaleController.getString(R.string.MediaDownloadsModeManualOnlyAbout);
            default:
                return LocaleController.getString(R.string.MediaDownloadsModeSmartAskAbout);
        }
    }

    private void showDownloadModeDialog() {
        final String[] labels = {
                LocaleController.getString(R.string.MediaDownloadsModeSmartAsk),
                LocaleController.getString(R.string.MediaDownloadsModeSelectedQuality),
                LocaleController.getString(R.string.MediaDownloadsModeOriginal),
                LocaleController.getString(R.string.MediaDownloadsModeDataSaver),
                LocaleController.getString(R.string.MediaDownloadsModeManualOnly)
        };
        final Dialog[] dialogRef = new Dialog[1];
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            RadioColorCell cell = new RadioColorCell(getContext());
            cell.setPadding(org.telegram.messenger.AndroidUtilities.dp(4), 0,
                    org.telegram.messenger.AndroidUtilities.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground),
                    Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(labels[i], mode == getUserConfig().mg.customMediaDownloadMode);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            cell.setOnClickListener(v -> {
                getUserConfig().mg.customMediaDownloadMode = mode;
                getUserConfig().saveConfig(false);
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
                refreshList();
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }
        Dialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.MediaDownloadsDownloadMode))
                .setView(layout)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        dialogRef[0] = dialog;
        dialog.show();
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
