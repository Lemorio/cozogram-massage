package it.belloworld.mercurygram.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ConcurrentHashMap;

/** Unified local rendition service for all media entry points. */
public final class MediaQualityPipeline {
    public interface Callback {
        void onComplete(File file, boolean processed);
    }

    private static final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    private MediaQualityPipeline() {
    }

    public static void requestAfterDownload(int account, String fileName, boolean video, int quality, Callback callback) {
        if (fileName == null || quality == MediaQualityHelper.ORIGINAL) {
            return;
        }
        NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int notificationAccount, Object... args) {
                if (id != NotificationCenter.fileLoaded || args.length < 2 || !fileName.equals(args[0])) {
                    return;
                }
                NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
                File source = args[1] instanceof File ? (File) args[1] : null;
                request(source, video, quality, callback);
            }
        };
        NotificationCenter.getInstance(account).addObserver(observer, NotificationCenter.fileLoaded);
    }

    public static void request(File source, boolean video, int quality, Callback callback) {
        if (source == null || !source.isFile() || quality == MediaQualityHelper.ORIGINAL) {
            notifyComplete(callback, source, false);
            return;
        }
        if (video) {
            MediaQualityTranscoder.processAsync(source, quality, callback::onComplete);
            return;
        }
        final File output = MediaQualityHelper.getProcessedPhotoFile(source, quality);
        if (output == null) {
            notifyComplete(callback, source, false);
            return;
        }
        if (MediaQualityHelper.hasProcessedPhoto(source, quality)) {
            notifyComplete(callback, output, true);
            return;
        }
        final String key = source.getAbsolutePath() + "#photo#" + MediaQualityHelper.clamp(quality);
        if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File result = resizePhoto(source, output, quality);
                notifyComplete(callback, result != null ? result : source, result != null);
            } finally {
                inFlight.remove(key);
            }
        });
    }

    private static File resizePhoto(File source, File output, int quality) {
        Bitmap bitmap = null;
        Bitmap scaled = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int target = MediaQualityHelper.getPhotoTargetSize(quality);
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / (sample * 2) >= target && sample < 16) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
            if (bitmap == null) {
                return null;
            }
            int currentLongest = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (currentLongest > target) {
                float scale = target / (float) currentLongest;
                int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
                int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
                scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
            } else {
                scaled = bitmap;
                bitmap = null;
            }
            File tmp = new File(output.getAbsolutePath() + ".tmp");
            try (FileOutputStream stream = new FileOutputStream(tmp)) {
                int compression = quality == MediaQualityHelper.LOW ? 70
                        : quality == MediaQualityHelper.HIGH ? 92 : 82;
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, compression, stream)) {
                    return null;
                }
            }
            if (!tmp.renameTo(output)) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                if (!tmp.renameTo(output)) {
                    return null;
                }
            }
            return output.isFile() && output.length() > 0 ? output : null;
        } catch (Throwable error) {
            FileLog.e("Media quality photo rendition failed", error);
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            return null;
        } finally {
            if (scaled != null) {
                scaled.recycle();
            }
            if (bitmap != null && bitmap != scaled) {
                bitmap.recycle();
            }
        }
    }

    private static void notifyComplete(Callback callback, File file, boolean processed) {
        if (callback != null) {
            callback.onComplete(file, processed);
        }
    }
}
