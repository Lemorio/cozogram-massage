package org.telegram.messenger;

import android.content.Context;

import it.belloworld.mercurygram.helpers.MediaQualityHelper;
import it.belloworld.mercurygram.helpers.MediaQualityPipeline;

import org.telegram.ui.ActionBar.AlertDialog;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;

/**
 * Manual media download entry point. This path is intentionally independent of
 * DownloadController's auto-download mask: a user-triggered download always
 * remains available when auto-download is disabled.
 */
public final class MediaDownloadController {
    public interface Callback {
        void onSelected(MessageObject messageObject, VideoPlayer.Quality videoQuality, TLRPC.PhotoSize photoSize);
    }

    public interface QualityCallback {
        void onSelected(MessageObject messageObject, VideoPlayer.Quality videoQuality, TLRPC.PhotoSize photoSize, int quality);
    }

    private MediaDownloadController() {
    }

    public static boolean hasConcreteChoices(MessageObject messageObject) {
        return messageObject != null && (messageObject.isVideo() && !videoQualities(messageObject).isEmpty()
                || messageObject.isPhoto() && !photoSizes(messageObject).isEmpty());
    }

    /** Uses the single global quality preference for explicit/manual download. */
    public static void chooseOrDownload(Context context, MessageObject messageObject, Callback callback) {
        selectGlobalQuality(messageObject, callback);
    }

    /** Explicit long-press download: choose a one-shot quality override. */
    public static void chooseAs(Context context, MessageObject messageObject, QualityCallback callback) {
        if (callback == null || messageObject == null) {
            return;
        }
        if (context == null || (!messageObject.isVideo() && !messageObject.isPhoto())) {
            if (messageObject.isVideo()) {
                callback.onSelected(messageObject, MediaQualityHelper.selectVideoQuality(videoQualities(messageObject), MediaQualityHelper.getQuality()), null, MediaQualityHelper.getQuality());
            } else if (messageObject.isPhoto()) {
                callback.onSelected(messageObject, null, MediaQualityHelper.selectPhotoSize(photoSizes(messageObject), MediaQualityHelper.getQuality()), MediaQualityHelper.getQuality());
            } else {
                callback.onSelected(messageObject, null, null, MediaQualityHelper.getQuality());
            }
            return;
        }
        String[] labels = buildQualityLabels(messageObject);
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MediaDownloadsDownloadMedia))
                .setItems(labels, (dialog, which) -> {
                    selectQuality(messageObject, which, (selectedMessage, videoQuality, photoSize) ->
                            callback.onSelected(selectedMessage, videoQuality, photoSize, which));
                    dialog.dismiss();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private static String[] buildQualityLabels(MessageObject messageObject) {
        String[] labels = new String[4];
        int[] qualities = {MediaQualityHelper.LOW, MediaQualityHelper.BALANCED,
                MediaQualityHelper.HIGH, MediaQualityHelper.ORIGINAL};
        String[] names = {"Low", "Balanced", "High", "Original"};
        for (int i = 0; i < qualities.length; i++) {
            long size = estimateSize(messageObject, qualities[i]);
            String detail;
            if (messageObject.isVideo()) {
                VideoPlayer.Quality quality = MediaQualityHelper.selectVideoQuality(
                        videoQualities(messageObject), qualities[i]);
                detail = quality == null ? "not available" : quality.p() + "p · " + AndroidUtilities.formatFileSize(size);
            } else {
                TLRPC.PhotoSize photo = MediaQualityHelper.selectPhotoSize(photoSizes(messageObject), qualities[i]);
                detail = photo == null ? "not available" : photo.w + "×" + photo.h + " · " + AndroidUtilities.formatFileSize(size);
            }
            labels[i] = names[i] + " · " + detail;
        }
        return labels;
    }

    private static long estimateSize(MessageObject messageObject, int quality) {
        if (messageObject.isVideo()) {
            VideoPlayer.Quality selected = MediaQualityHelper.selectVideoQuality(videoQualities(messageObject), quality);
            VideoPlayer.VideoUri uri = selected == null ? null : selected.getDownloadUri();
            return uri == null ? 0 : uri.size;
        }
        TLRPC.PhotoSize selected = MediaQualityHelper.selectPhotoSize(photoSizes(messageObject), quality);
        return selected == null ? 0 : selected.size;
    }

    private static void selectQuality(MessageObject messageObject, int quality, Callback callback) {
        if (messageObject.isVideo()) {
            callback.onSelected(messageObject,
                    MediaQualityHelper.selectVideoQuality(videoQualities(messageObject), quality), null);
        } else if (messageObject.isPhoto()) {
            callback.onSelected(messageObject, null,
                    MediaQualityHelper.selectPhotoSize(photoSizes(messageObject), quality));
        } else {
            callback.onSelected(messageObject, null, null);
        }
    }

    private static void selectGlobalQuality(MessageObject messageObject, Callback callback) {
        if (messageObject == null || callback == null) {
            return;
        }
        if (messageObject.isVideo()) {
            VideoPlayer.Quality selected = MediaQualityHelper.selectVideoQuality(
                    videoQualities(messageObject), MediaQualityHelper.getQuality());
            callback.onSelected(messageObject, selected, null);
        } else if (messageObject.isPhoto()) {
            TLRPC.PhotoSize selected = MediaQualityHelper.selectPhotoSize(
                    photoSizes(messageObject), MediaQualityHelper.getQuality());
            callback.onSelected(messageObject, null, selected);
        } else {
            callback.onSelected(messageObject, null, null);
        }
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
        if (messageObject == null) {
            return result;
        }
        ArrayList<TLRPC.PhotoSize> source = messageObject.photoThumbs;
        if (source == null || source.isEmpty()) {
            TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
            if (media instanceof TLRPC.TL_messageMediaPhoto && ((TLRPC.TL_messageMediaPhoto) media).photo != null) {
                source = ((TLRPC.TL_messageMediaPhoto) media).photo.sizes;
            }
        }
        if (source == null) {
            return result;
        }
        for (TLRPC.PhotoSize size : source) {
            if (size != null && size.w > 0 && size.h > 0 && size.size > 0
                    && !(size instanceof TLRPC.TL_photoStrippedSize)) {
                result.add(size);
            }
        }
        return result;
    }

    public static void downloadSelected(MessageObject messageObject, VideoPlayer.Quality videoQuality, TLRPC.PhotoSize photoSize) {
        downloadSelected(messageObject, videoQuality, photoSize, MediaQualityHelper.getQuality());
    }

    public static void downloadSelected(MessageObject messageObject, VideoPlayer.Quality videoQuality, TLRPC.PhotoSize photoSize, int quality) {
        if (messageObject == null) {
            return;
        }
        messageObject.putInDownloadsStore = true;
        if (videoQuality != null) {
            VideoPlayer.VideoUri uri = videoQuality.getDownloadUri();
            if (uri != null && uri.document != null) {
                String fileName = FileLoader.getAttachFileName(uri.document);
                MediaQualityPipeline.requestAfterDownload(messageObject.currentAccount, fileName, true, quality, null);
                FileLoader.getInstance(messageObject.currentAccount).loadFile(
                        uri.document, messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
            }
        } else if (photoSize != null) {
            TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
            TLRPC.Photo photo = media instanceof TLRPC.TL_messageMediaPhoto
                    ? ((TLRPC.TL_messageMediaPhoto) media).photo : null;
            ImageLocation location = ImageLocation.getForPhoto(photoSize, photo);
            if (location != null) {
                String fileName = FileLoader.getAttachFileName(photoSize);
                MediaQualityPipeline.requestAfterDownload(messageObject.currentAccount, fileName, false, quality, null);
                FileLoader.getInstance(messageObject.currentAccount).loadFile(
                        location, messageObject, null, FileLoader.PRIORITY_NORMAL_UP, 0);
            }
        } else if (messageObject.getDocument() != null) {
            FileLoader.getInstance(messageObject.currentAccount).loadFile(
                    messageObject.getDocument(), messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
        }
    }
}
