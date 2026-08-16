package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import org.telegram.ui.Components.Bulletin;

import java.util.HashSet;

/**
 * Local-only network policy used while a voice, video, or group call is active.
 * It never changes call signaling/media transport and never cancels a transfer
 * that is already running. Automatic media loads are classified before they
 * enter FileLoader; explicit user downloads are promoted instead.
 */
public final class CallNetworkPriorityController implements NotificationCenter.NotificationCenterDelegate {
    public static final int MODE_BALANCED = 0;
    public static final int MODE_STRICT = 1;
    public static final int MODE_OFF = 2;

    private static final CallNetworkPriorityController[] INSTANCES = new CallNetworkPriorityController[UserConfig.MAX_ACCOUNT_COUNT];

    private final int currentAccount;
    private boolean callActive;
    private boolean bulletinShownForCall;
    private final HashSet<String> deferredAutomaticFiles = new HashSet<>();

    public static CallNetworkPriorityController getInstance(int account) {
        CallNetworkPriorityController controller = INSTANCES[account];
        if (controller == null) {
            synchronized (INSTANCES) {
                controller = INSTANCES[account];
                if (controller == null) {
                    controller = INSTANCES[account] = new CallNetworkPriorityController(account);
                }
            }
        }
        return controller;
    }

    private CallNetworkPriorityController(int account) {
        currentAccount = account;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didStartedCall);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didEndCall);
        if (org.telegram.messenger.voip.VoIPService.getSharedInstance() != null) {
            callActive = true;
        }
    }

    public boolean isCallActive() {
        return callActive;
    }

    public boolean isEnabled() {
        return UserConfig.getInstance(currentAccount).mg.prioritizeNetworkDuringCall;
    }

    public int getMode() {
        int mode = UserConfig.getInstance(currentAccount).mg.callNetworkRestrictionMode;
        return mode >= MODE_BALANCED && mode <= MODE_OFF ? mode : MODE_BALANCED;
    }

    public boolean isPolicyActive() {
        return callActive && isEnabled() && getMode() != MODE_OFF;
    }

    /** True only for an automatic/prefetch request that may be deferred. */
    public boolean shouldDeferAutomatic(MessageObject messageObject) {
        if (!isPolicyActive() || messageObject == null || messageObject.putInDownloadsStore) {
            return false;
        }
        boolean defer;
        if (getMode() == MODE_STRICT) {
            defer = true;
        } else {
            long size = MessageObject.getMessageSize(messageObject.messageOwner);
            defer = size > 2L * 1024L * 1024L || messageObject.isVideo() || messageObject.isDocument();
        }
        if (defer && messageObject.getFileName() != null) {
            deferredAutomaticFiles.add(messageObject.getFileName());
        }
        return defer;
    }

    public boolean shouldDeferAutomatic(TLRPC.Message message) {
        if (!isPolicyActive() || message == null) {
            return false;
        }
        if (getMode() == MODE_STRICT) {
            return true;
        }
        long size = MessageObject.getMessageSize(message);
        return size > 2L * 1024L * 1024L || MessageObject.isVideoMessage(message)
                || MessageObject.isGifMessage(message) || MessageObject.isRoundVideoMessage(message);
    }

    /** Explicit user downloads outrank autoplay/prefetch while a call is active. */
    public int getRequestedPriority(MessageObject messageObject, int requestedPriority) {
        if (callActive && isEnabled() && messageObject != null && messageObject.putInDownloadsStore) {
            return FileLoader.PRIORITY_HIGH;
        }
        return requestedPriority;
    }

    public boolean isPausedDuringCall(MessageObject messageObject) {
        return isPolicyActive() && messageObject != null
                && deferredAutomaticFiles.contains(messageObject.getFileName())
                && !FileLoader.getInstance(currentAccount).isLoadingFile(messageObject.getFileName());
    }

    public void resume(MessageObject messageObject) {
        if (messageObject == null || messageObject.getDocument() == null) {
            return;
        }
        messageObject.putInDownloadsStore = true;
        FileLoader.getInstance(currentAccount).loadFile(
                messageObject.getDocument(), messageObject,
                getRequestedPriority(messageObject, FileLoader.PRIORITY_LOW), 0);
        DownloadController.getInstance(currentAccount).updateFilesLoadingPriority();
    }

    public void notifyPolicyChanged() {
        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
        DownloadController.getInstance(currentAccount).updateFilesLoadingPriority();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didStartedCall) {
            callActive = true;
            bulletinShownForCall = false;
            if (isPolicyActive() && !bulletinShownForCall) {
                bulletinShownForCall = true;
                NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.showBulletin,
                        Bulletin.TYPE_SUCCESS,
                        "Network priority enabled — Background downloads are temporarily limited."
                );
            }
            notifyPolicyChanged();
        } else if (id == NotificationCenter.didEndCall) {
            callActive = false;
            bulletinShownForCall = false;
            deferredAutomaticFiles.clear();
            // Re-evaluate automatic eligibility; this does not force all files
            // to start and does not interrupt any transfer that was in flight.
            notifyPolicyChanged();
        }
    }
}

// Kept package-private intentionally; this controller has one instance per
// account and does not expose server-facing state.
@SuppressWarnings("unused")
final class CallNetworkPriorityControllerMarker {}
