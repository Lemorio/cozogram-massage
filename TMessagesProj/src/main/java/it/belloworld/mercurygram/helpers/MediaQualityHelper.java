package it.belloworld.mercurygram.helpers;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.ArrayList;

/**
 * Central policy for the user-selected media quality.
 *
 * This class only decides quality and paths. Heavy video processing is added by
 * the video pipeline phase and must never run on the UI thread.
 */
public final class MediaQualityHelper {
    public static final int LOW = 0;
    public static final int BALANCED = 1;
    public static final int HIGH = 2;
    public static final int ORIGINAL = 3;

    private static final int PHOTO_LOW_TARGET = 480;
    private static final int PHOTO_BALANCED_TARGET = 1280;
    private static final int PHOTO_HIGH_TARGET = 2560;

    private MediaQualityHelper() {
    }

    public static int getQuality() {
        return clamp(SharedConfig.mg_mediaQuality);
    }

    public static void setQuality(int quality) {
        SharedConfig.mg_mediaQuality = clamp(quality);
    }

    public static int clamp(int quality) {
        return Math.max(LOW, Math.min(ORIGINAL, quality));
    }

    /** Returns the maximum video height for a quality, or -1 for Original. */
    public static int getVideoTargetHeight(int quality) {
        switch (clamp(quality)) {
            case LOW:
                return 480;
            case BALANCED:
                return 720;
            case HIGH:
                return 1080;
            default:
                return -1;
        }
    }

    public static boolean shouldTranscodeVideo(int sourceHeight, int quality) {
        int target = getVideoTargetHeight(quality);
        return target > 0 && sourceHeight > target;
    }

    /**
     * Selects a real server PhotoSize. The selector prefers a size at or above
     * the family target, then falls back to the largest size below it. It never
     * creates or claims a size that is not present in the server response.
     */
    public static TLRPC.PhotoSize selectPhotoSize(ArrayList<TLRPC.PhotoSize> sizes, int quality) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        ArrayList<TLRPC.PhotoSize> usable = new ArrayList<>();
        for (TLRPC.PhotoSize size : sizes) {
            if (size == null || size.w <= 0 || size.h <= 0 || size instanceof TLRPC.TL_photoStrippedSize) {
                continue;
            }
            if (size.size <= 0 && !(size instanceof TLRPC.TL_photoCachedSize)) {
                continue;
            }
            usable.add(size);
        }
        if (usable.isEmpty()) {
            return null;
        }

        TLRPC.PhotoSize largest = usable.get(0);
        for (TLRPC.PhotoSize size : usable) {
            if (area(size) > area(largest)) {
                largest = size;
            }
        }
        if (clamp(quality) == ORIGINAL) {
            return largest;
        }

        int target = getPhotoTargetSize(quality);
        TLRPC.PhotoSize firstAtOrAbove = null;
        TLRPC.PhotoSize largestBelow = null;
        for (TLRPC.PhotoSize size : usable) {
            int side = Math.max(size.w, size.h);
            if (side >= target && (firstAtOrAbove == null || area(size) < area(firstAtOrAbove))) {
                firstAtOrAbove = size;
            }
            if (side < target && (largestBelow == null || area(size) > area(largestBelow))) {
                largestBelow = size;
            }
        }
        return firstAtOrAbove != null ? firstAtOrAbove : (largestBelow != null ? largestBelow : largest);
    }

    public static VideoPlayer.Quality selectVideoQuality(ArrayList<VideoPlayer.Quality> qualities, int quality) {
        if (qualities == null || qualities.isEmpty()) {
            return null;
        }
        VideoPlayer.Quality largest = qualities.get(0);
        for (VideoPlayer.Quality candidate : qualities) {
            if (candidate != null && candidate.p() > largest.p()) {
                largest = candidate;
            }
        }
        if (clamp(quality) == ORIGINAL) {
            return largest;
        }
        int target = getVideoTargetHeight(quality);
        VideoPlayer.Quality bestAtOrBelow = null;
        VideoPlayer.Quality smallestAbove = null;
        for (VideoPlayer.Quality candidate : qualities) {
            if (candidate == null || candidate.getDownloadUri() == null) {
                continue;
            }
            int height = candidate.p();
            if (height <= target && (bestAtOrBelow == null || height > bestAtOrBelow.p())) {
                bestAtOrBelow = candidate;
            }
            if (height > target && (smallestAbove == null || height < smallestAbove.p())) {
                smallestAbove = candidate;
            }
        }
        return bestAtOrBelow != null ? bestAtOrBelow : (smallestAbove != null ? smallestAbove : largest);
    }

    public static int getPhotoTargetSize(int quality) {
        switch (clamp(quality)) {
            case LOW:
                return PHOTO_LOW_TARGET;
            case BALANCED:
                return PHOTO_BALANCED_TARGET;
            case HIGH:
                return PHOTO_HIGH_TARGET;
            default:
                return Integer.MAX_VALUE;
        }
    }

    public static File getProcessedPhotoFile(File source, int quality) {
        if (source == null || !source.isFile() || clamp(quality) == ORIGINAL) {
            return null;
        }
        return new File(source.getParentFile(), source.getName() + ".mgq" + clamp(quality) + ".jpg");
    }

    public static File getProcessedVideoFile(File source, int quality) {
        if (source == null || !source.isFile() || clamp(quality) == ORIGINAL) {
            return null;
        }
        return new File(source.getParentFile(), source.getName() + ".mgq" + clamp(quality) + ".mp4");
    }

    public static boolean hasProcessedPhoto(File source, int quality) {
        File processed = getProcessedPhotoFile(source, quality);
        return processed != null && processed.isFile() && processed.length() > 0;
    }

    public static boolean hasProcessedVideo(File source, int quality) {
        File processed = getProcessedVideoFile(source, quality);
        return processed != null && processed.isFile() && processed.length() > 0;
    }

    /** Resolves a real photo path without downloading or inventing a variant. */
    public static File getPhotoPath(int account, TLRPC.PhotoSize size, boolean encrypt) {
        if (size == null) {
            return null;
        }
        return FileLoader.getInstance(account).getPathToAttach(size, encrypt);
    }

    private static long area(TLRPC.PhotoSize size) {
        return (long) size.w * size.h;
    }
}

