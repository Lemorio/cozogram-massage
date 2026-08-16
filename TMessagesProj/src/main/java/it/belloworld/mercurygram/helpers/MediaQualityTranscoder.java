package it.belloworld.mercurygram.helpers;

import android.media.MediaMetadataRetriever;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.video.MediaCodecVideoConvertor;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local video quality processor. All conversion work is posted to the global
 * worker queue; callers must not invoke the converter from the UI thread.
 */
public final class MediaQualityTranscoder {
    private static final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();
    public interface Callback {
        void onComplete(File output, boolean processed);
    }

    private MediaQualityTranscoder() {
    }

    public static int getVideoHeight(File source) {
        if (source == null || !source.isFile()) {
            return 0;
        }
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(source.getAbsolutePath());
            return parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
        } catch (Throwable ignored) {
            return 0;
        } finally {
            try {
                metadata.release();
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isProcessedVideoFile(File file) {
        return file != null && file.getName().contains(".mgq");
    }

    public static void processAsync(final File source, final int quality, final Callback callback) {
        if (source == null || !source.isFile() || quality == MediaQualityHelper.ORIGINAL) {
            notifyComplete(callback, source, false);
            return;
        }
        final File output = MediaQualityHelper.getProcessedVideoFile(source, quality);
        if (output == null) {
            notifyComplete(callback, source, false);
            return;
        }
        if (MediaQualityHelper.hasProcessedVideo(source, quality)) {
            notifyComplete(callback, output, true);
            return;
        }
        final String jobKey = source.getAbsolutePath() + "#" + MediaQualityHelper.clamp(quality);
        if (inFlight.putIfAbsent(jobKey, Boolean.TRUE) != null) {
            return;
        }

        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            try {
                File result = transcode(source, output, quality);
                notifyComplete(callback, result != null ? result : source, result != null);
            } finally {
                inFlight.remove(jobKey);
            }
        });
    }

    private static File transcode(File source, File output, int quality) {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(source.getAbsolutePath());
            int sourceWidth = parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 0);
            int sourceHeight = parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
            long duration = parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), 0L);
            int rotation = parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION), 0);
            int targetHeight = MediaQualityHelper.getVideoTargetHeight(quality);
            if (sourceWidth <= 0 || sourceHeight <= 0 || duration <= 0 || targetHeight <= 0
                    || !MediaQualityHelper.shouldTranscodeVideo(sourceHeight, quality)) {
                return null;
            }

            int resultHeight = Math.min(sourceHeight, targetHeight);
            int resultWidth = Math.max(2, Math.round(sourceWidth * (resultHeight / (float) sourceHeight)));
            if ((resultWidth & 1) != 0) {
                resultWidth--;
            }
            if ((resultHeight & 1) != 0) {
                resultHeight--;
            }
            int bitrate = bitrateFor(quality, resultWidth, resultHeight);
            VideoEditedInfo info = new VideoEditedInfo();
            info.originalPath = source.getAbsolutePath();
            info.originalWidth = sourceWidth;
            info.originalHeight = sourceHeight;
            info.resultWidth = resultWidth;
            info.resultHeight = resultHeight;
            info.rotationValue = rotation;
            info.framerate = 30;
            info.bitrate = bitrate;
            info.originalBitrate = 0;
            info.startTime = 0;
            info.endTime = 0;
            info.avatarStartTime = -1;
            info.originalDuration = duration;
            info.estimatedDuration = duration;
            info.muted = false;
            info.volume = 1f;

            MediaController.VideoConvertorListener listener = new MediaController.VideoConvertorListener() {
                @Override
                public boolean checkConversionCanceled() {
                    return false;
                }

                @Override
                public void didWriteData(long availableSize, float progress) {
                    // The regular download UI remains responsible for progress.
                }
            };
            MediaCodecVideoConvertor.ConvertVideoParams params = MediaCodecVideoConvertor.ConvertVideoParams.of(
                    source.getAbsolutePath(), output, 0L, rotation, false,
                    sourceWidth, sourceHeight, resultWidth, resultHeight,
                    30, bitrate, 0, 0L, 0L, -1L,
                    true, duration, listener, info
            );
            if (!new MediaCodecVideoConvertor().convertVideo(params)) {
                deletePartial(output);
                return null;
            }
            return output.isFile() && output.length() > 0 ? output : null;
        } catch (Throwable error) {
            FileLog.e("Media quality transcode failed", error);
            deletePartial(output);
            return null;
        } finally {
            try {
                metadata.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static int bitrateFor(int quality, int width, int height) {
        // Conservative AVC targets: SD ~0.8 Mbps, HD ~1.8 Mbps, FHD ~4 Mbps.
        switch (MediaQualityHelper.clamp(quality)) {
            case MediaQualityHelper.LOW:
                return Math.max(500_000, width * height * 2);
            case MediaQualityHelper.HIGH:
                return Math.max(2_500_000, width * height * 4);
            default:
                return Math.max(1_000_000, width * height * 3);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void deletePartial(File output) {
        if (output != null && output.exists()) {
            // Never expose a partial processed file as a valid cache hit.
            //noinspection ResultOfMethodCallIgnored
            output.delete();
        }
    }

    private static void notifyComplete(Callback callback, File file, boolean processed) {
        if (callback != null) {
            callback.onComplete(file, processed);
        }
    }
}
