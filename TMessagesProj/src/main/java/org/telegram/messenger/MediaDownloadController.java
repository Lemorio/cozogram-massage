package org.telegram.messenger;

import android.content.Context;

import it.belloworld.mercurygram.helpers.MediaQualityHelper;

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

    /** Download-as now follows the same global policy; no second quality menu is shown. */
    public static void chooseAs(Context context, MessageObject messageObject, Callback callback) {
        selectGlobalQuality(messageObject, callback);
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
        if (messageObject == null) {
            return;
        }
        messageObject.putInDownloadsStore = true;
        if (videoQuality != null) {
            VideoPlayer.VideoUri uri = videoQuality.getDownloadUri();
            if (uri != null && uri.document != null) {
                FileLoader.getInstance(messageObject.currentAccount).loadFile(
                        uri.document, messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
            }
        } else if (photoSize != null) {
            TLRPC.MessageMedia media = MessageObject.getMedia(messageObject);
            TLRPC.Photo photo = media instanceof TLRPC.TL_messageMediaPhoto
                    ? ((TLRPC.TL_messageMediaPhoto) media).photo : null;
            ImageLocation location = ImageLocation.getForPhoto(photoSize, photo);
            if (location != null) {
                FileLoader.getInstance(messageObject.currentAccount).loadFile(
                        location, messageObject, null, FileLoader.PRIORITY_NORMAL_UP, 0);
            }
        } else if (messageObject.getDocument() != null) {
            FileLoader.getInstance(messageObject.currentAccount).loadFile(
                    messageObject.getDocument(), messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
        }
    }
}
