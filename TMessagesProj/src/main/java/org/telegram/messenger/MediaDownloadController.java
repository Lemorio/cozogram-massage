package org.telegram.messenger;

import android.content.Context;
import android.content.DialogInterface;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Fase B media download path. It reads only variants present in the message
 * media object; it never synthesizes resolution or size choices.
 */
public final class MediaDownloadController {

    public interface Callback {
        void onSelected(MessageObject messageObject, VideoPlayer.Quality videoQuality, TLRPC.PhotoSize photoSize);
    }

    private MediaDownloadController() {
    }

    public static boolean hasConcreteChoices(MessageObject messageObject) {
        return !videoQualities(messageObject).isEmpty() || photoSizes(messageObject).size() > 1;
    }

    public static void chooseAs(Context context, MessageObject messageObject, Callback callback) {
        if (messageObject != null && messageObject.isVideo() && !videoQualities(messageObject).isEmpty()) {
            ArrayList<VideoPlayer.Quality> qualities = videoQualities(messageObject);
            if (qualities.size() > 1) {
                showVideoPicker(context, messageObject, qualities, callback);
                return;
            }
        }
        if (messageObject != null && messageObject.isPhoto()) {
            ArrayList<TLRPC.PhotoSize> photos = photoSizes(messageObject);
            if (photos.size() > 1) {
                showPhotoPicker(context, messageObject, photos, callback);
                return;
            }
        }
        chooseOrDownload(context, messageObject, callback);
    }

    public static void chooseOrDownload(Context context, MessageObject messageObject, Callback callback) {
        if (messageObject == null) {
            return;
        }
        if (messageObject.isVideo() && !videoQualities(messageObject).isEmpty()) {
            ArrayList<VideoPlayer.Quality> qualities = videoQualities(messageObject);
            if (qualities.size() > 1 || getMode(messageObject) == 0) {
                showVideoPicker(context, messageObject, qualities, callback);
            } else {
                selectVideo(messageObject, qualities, getMode(messageObject), callback);
            }
            return;
        }
        ArrayList<TLRPC.PhotoSize> photos = photoSizes(messageObject);
        if (messageObject.isPhoto() && !photos.isEmpty()) {
            if (getMode(messageObject) == 0) {
                showPhotoPicker(context, messageObject, photos, callback);
            } else {
                selectPhoto(messageObject, photos, getMode(messageObject), callback);
            }
            return;
        }
        callback.onSelected(messageObject, null, null);
    }

    public static ArrayList<VideoPlayer.Quality> videoQualities(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.media == null
                || messageObject.messageOwner.media.document == null) {
            return new ArrayList<>();
        }
        ArrayList<VideoPlayer.Quality> result = VideoPlayer.getQualities(
                messageObject.currentAccount, messageObject.messageOwner.media, false);
        return result == null ? new ArrayList<>() : result;
    }

    public static ArrayList<TLRPC.PhotoSize> photoSizes(MessageObject messageObject) {
        ArrayList<TLRPC.PhotoSize> result = new ArrayList<>();
        if (messageObject == null || messageObject.photoThumbs == null) {
            return result;
        }
        for (TLRPC.PhotoSize size : messageObject.photoThumbs) {
            if (size == null || size.w <= 0 || size.h <= 0 || size.size <= 0 || size instanceof TLRPC.TL_photoStrippedSize) {
                continue;
            }
            boolean duplicate = false;
            for (TLRPC.PhotoSize existing : result) {
                if (existing.w == size.w && existing.h == size.h && existing.size == size.size) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(size);
            }
        }
        Collections.sort(result, Comparator.comparingLong(size -> size.size));
        return result;
    }

    private static int getMode(MessageObject messageObject) {
        return UserConfig.getInstance(messageObject.currentAccount).mg.customMediaDownloadMode;
    }

    private static void showVideoPicker(Context context, MessageObject messageObject,
                                        ArrayList<VideoPlayer.Quality> qualities, Callback callback) {
        String[] labels = new String[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) {
            VideoPlayer.VideoUri uri = qualities.get(i).getDownloadUri();
            labels[i] = qualities.get(i).p() + "p · " + AndroidUtilities.formatFileSize(uri == null ? 0 : uri.size);
        }
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MediaDownloadsChooseVideo))
                .setItems(labels, (dialog, which) -> {
                    callback.onSelected(messageObject, qualities.get(which), null);
                    dialog.dismiss();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private static void showPhotoPicker(Context context, MessageObject messageObject,
                                        ArrayList<TLRPC.PhotoSize> photos, Callback callback) {
        String[] labels = new String[photos.size()];
        for (int i = 0; i < photos.size(); i++) {
            TLRPC.PhotoSize size = photos.get(i);
            labels[i] = size.w + "×" + size.h + " · " + AndroidUtilities.formatFileSize(size.size);
        }
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MediaDownloadsChoosePhoto))
                .setItems(labels, (dialog, which) -> {
                    callback.onSelected(messageObject, null, photos.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private static void selectVideo(MessageObject messageObject, ArrayList<VideoPlayer.Quality> qualities, int mode, Callback callback) {
        ArrayList<VideoPlayer.Quality> sorted = new ArrayList<>(qualities);
        Collections.sort(sorted, (left, right) -> Long.compare(downloadSize(left), downloadSize(right)));
        int index = mode == 2 ? sorted.size() - 1 : mode == 3 ? 0 : Math.max(0, (sorted.size() - 1) / 2);
        callback.onSelected(messageObject, sorted.get(index), null);
    }

    private static long downloadSize(VideoPlayer.Quality quality) {
        VideoPlayer.VideoUri uri = quality == null ? null : quality.getDownloadUri();
        return uri == null || uri.size <= 0 ? Long.MAX_VALUE : uri.size;
    }

    private static void selectPhoto(MessageObject messageObject, ArrayList<TLRPC.PhotoSize> photos, int mode, Callback callback) {
        int index = mode == 2 ? photos.size() - 1 : mode == 3 ? 0 : Math.max(0, (photos.size() - 1) / 2);
        callback.onSelected(messageObject, null, photos.get(index));
    }

    public static void downloadSelected(MessageObject messageObject, VideoPlayer.Quality videoQuality, TLRPC.PhotoSize photoSize) {
        if (messageObject == null) {
            return;
        }
        messageObject.putInDownloadsStore = true;
        if (videoQuality != null) {
            VideoPlayer.VideoUri uri = videoQuality.getDownloadUri();
            if (uri != null && uri.document != null) {
                FileLoader.getInstance(messageObject.currentAccount).loadFile(uri.document, messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
            }
        } else if (photoSize != null) {
            TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
            TLRPC.Photo photo = media instanceof TLRPC.TL_messageMediaPhoto
                    ? ((TLRPC.TL_messageMediaPhoto) media).photo : null;
            ImageLocation location = ImageLocation.getForPhoto(photoSize, photo);
            if (location != null) {
                FileLoader.getInstance(messageObject.currentAccount).loadFile(location, messageObject, null, FileLoader.PRIORITY_NORMAL_UP, 0);
            }
        } else if (messageObject.getDocument() != null) {
            FileLoader.getInstance(messageObject.currentAccount).loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
        }
    }
}
