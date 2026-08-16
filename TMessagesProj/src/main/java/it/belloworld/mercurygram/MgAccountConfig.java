package it.belloworld.mercurygram;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

/**
 * Mercurygram per-account settings. Lives in the same per-account
 * userconfig / userconfig&lt;N&gt; SharedPreferences file as the rest of
 * {@link org.telegram.messenger.UserConfig} (pref keys unchanged, no
 * migration); UserConfig owns one instance and calls save/load/reset.
 */
public class MgAccountConfig {

    public boolean sendLargePhotos = false;
    public boolean rearRoundCamera = false;
    public boolean hideChatKeyboard = false;
    public boolean hideAllTab = false;
    public int defaultFolderId = 0;
    public boolean messageDetailsMenu = false;
    public boolean disableLivePhotosByDefault = false;
    public boolean savedMessagesHistory = false;
    public String transcribeLang = SharedConfig.MG_TRANSCRIBE_LANG_DEVICE;
    public boolean hideStories = false;
    public boolean hidePremiumPromo = false;
    public boolean disableGlobalSearch = false;
    public boolean disableAiEditor = false;
    public boolean disableAiSummary = false;
    public boolean disableInstantView = false;
    public boolean disableLinkPreviews = false;
    public boolean preferSecretChats = false;
    public boolean deleteForAllByDefault = false;
    public boolean stripTrackingParams = false;
    public boolean disableCloudDrafts = false;
    public boolean confirmInternalLinks = false;
    public boolean showCharCounter = false;
    public boolean quickSaveSideButtonEnabled = false;
    public int quickSaveSideButtonMode = 0; // 0 interaction, 1 always, 2 media only
    public int quickSavePresentationMode = 0; // 0 merge with Forward, 1 separate button
    public boolean hideJoinNotifications = false;
    public boolean autoRevealSpoilers = false;
    public boolean activeVideoChatsToTop = true;
    public boolean muteAllLocally = false;
    public boolean prioritizeNetworkDuringCall = true;
    public int callNetworkRestrictionMode = 0; // 0 Balanced, 1 Strict, 2 Off
    // Media Download Control (section 17); UI behavior is introduced phase by phase.
    public int customMediaDownloadMode = 0; // 0 Smart Ask, 1 selected, 2 original, 3 data saver, 4 manual only
    public int customVideoQualityPreset = 1; // Balanced placeholder until variant picker phase
    public int customPhotoQualityPreset = 1; // Balanced placeholder until variant picker phase
    public int customAudioDownloadPolicy = 0;
    public long customFileDownloadSizeLimit = 10L * 1024L * 1024L;
    public boolean customAskBeforeLargeDownload = true;
    public int customDownloadProfileMobile = 0;
    public int customDownloadProfileWifi = 0;
    public int customDownloadProfileRoaming = 0;
    public boolean customDownloadAlbumAsGroup = false;
    public boolean customKeepOriginalMedia = false;
    public boolean customMediaAutoCleanup = true;

    /**
     * Whether {@code draftMessage} may go to the server. An empty draft carries no
     * content, so let the clear reach the server even with cloud drafts off:
     * otherwise a draft stored before the toggle was enabled stays on the server
     * forever and gets pushed back into the composer on the next sync.
     *
     * <p>The emptiness test enumerates TL fields that change on every layer bump, so
     * it lives here rather than inline in MediaDataController.
     */
    public boolean allowsCloudSync(TLRPC.DraftMessage draftMessage) {
        if (!disableCloudDrafts) {
            return true;
        }
        return TextUtils.isEmpty(draftMessage.message)
            && (draftMessage.reply_to == null || draftMessage.reply_to.reply_to_msg_id == 0)
            && draftMessage.effect == 0
            && draftMessage.rich_message == null
            && draftMessage.suggested_post == null;
    }

    // MG: the reduced temp-key TTL ladder (1h→6h→24h) exhausted on this
    // account — server kept rejecting bindTempAuthKey, so native reduced
    // mode was force-disabled here while the global SharedConfig toggle
    // stays on (other accounts may still be running reduced mode). Surfaced
    // as a footer under Settings → Mercurygram → Privacy so the user is
    // not silently downgraded. Reset when the user re-enables the global
    // toggle (off→on cycle).
    public boolean mgReducedTrackingExhausted = false;

    public void save(SharedPreferences.Editor editor) {
        editor.putBoolean("sendLargePhotos", sendLargePhotos);
        editor.putBoolean("rearRoundCamera", rearRoundCamera);
        editor.putBoolean("hideChatKeyboard", hideChatKeyboard);
        editor.putBoolean("hideAllTab", hideAllTab);
        editor.putInt("defaultFolderId", defaultFolderId);
        editor.putBoolean("messageDetailsMenu", messageDetailsMenu);
        editor.putBoolean("disableLivePhotosByDefault", disableLivePhotosByDefault);
        editor.putBoolean("savedMessagesHistory", savedMessagesHistory);
        editor.putString("transcribeLang", transcribeLang);
        editor.putBoolean("hideStories", hideStories);
        editor.putBoolean("hidePremiumPromo", hidePremiumPromo);
        editor.putBoolean("disableGlobalSearch", disableGlobalSearch);
        editor.putBoolean("disableAiEditor", disableAiEditor);
        editor.putBoolean("disableAiSummary", disableAiSummary);
        editor.putBoolean("disableInstantView", disableInstantView);
        editor.putBoolean("disableLinkPreviews", disableLinkPreviews);
        editor.putBoolean("preferSecretChats", preferSecretChats);
        editor.putBoolean("deleteForAllByDefault", deleteForAllByDefault);
        editor.putBoolean("stripTrackingParams", stripTrackingParams);
        editor.putBoolean("disableCloudDrafts", disableCloudDrafts);
        editor.putBoolean("confirmInternalLinks", confirmInternalLinks);
        editor.putBoolean("showCharCounter", showCharCounter);
        editor.putBoolean("quickSaveSideButtonEnabled", quickSaveSideButtonEnabled);
        editor.putInt("quickSaveSideButtonMode", quickSaveSideButtonMode);
        editor.putInt("quickSavePresentationMode", quickSavePresentationMode);
        editor.putBoolean("hideJoinNotifications", hideJoinNotifications);
        editor.putBoolean("autoRevealSpoilers", autoRevealSpoilers);
        editor.putBoolean("activeVideoChatsToTop", activeVideoChatsToTop);
        editor.putBoolean("muteAllLocally", muteAllLocally);
        editor.putBoolean("prioritizeNetworkDuringCall", prioritizeNetworkDuringCall);
        editor.putInt("callNetworkRestrictionMode", callNetworkRestrictionMode);
        editor.putInt("custom_media_download_mode", customMediaDownloadMode);
        editor.putInt("custom_video_quality_preset", customVideoQualityPreset);
        editor.putInt("custom_photo_quality_preset", customPhotoQualityPreset);
        editor.putInt("custom_audio_download_policy", customAudioDownloadPolicy);
        editor.putLong("custom_file_download_size_limit", customFileDownloadSizeLimit);
        editor.putBoolean("custom_ask_before_large_download", customAskBeforeLargeDownload);
        editor.putInt("custom_download_profile_mobile", customDownloadProfileMobile);
        editor.putInt("custom_download_profile_wifi", customDownloadProfileWifi);
        editor.putInt("custom_download_profile_roaming", customDownloadProfileRoaming);
        editor.putBoolean("custom_download_album_as_group", customDownloadAlbumAsGroup);
        editor.putBoolean("custom_keep_original_media", customKeepOriginalMedia);
        editor.putBoolean("custom_media_auto_cleanup", customMediaAutoCleanup);
        editor.putBoolean("mgReducedTrackingExhausted", mgReducedTrackingExhausted);
    }

    public void load(SharedPreferences preferences) {
        sendLargePhotos = preferences.getBoolean("sendLargePhotos", false);
        rearRoundCamera = preferences.getBoolean("rearRoundCamera", false);
        hideChatKeyboard = preferences.getBoolean("hideChatKeyboard", false);
        hideAllTab = preferences.getBoolean("hideAllTab", false);
        defaultFolderId = preferences.getInt("defaultFolderId", 0);
        messageDetailsMenu = preferences.getBoolean("messageDetailsMenu", false);
        disableLivePhotosByDefault = preferences.getBoolean("disableLivePhotosByDefault", false);
        savedMessagesHistory = preferences.getBoolean("savedMessagesHistory", false);
        transcribeLang = preferences.getString("transcribeLang", SharedConfig.MG_TRANSCRIBE_LANG_DEVICE);
        hideStories = preferences.getBoolean("hideStories", false);
        hidePremiumPromo = preferences.getBoolean("hidePremiumPromo", false);
        disableGlobalSearch = preferences.getBoolean("disableGlobalSearch", false);
        disableAiEditor = preferences.getBoolean("disableAiEditor", false);
        disableAiSummary = preferences.getBoolean("disableAiSummary", false);
        disableInstantView = preferences.getBoolean("disableInstantView", false);
        disableLinkPreviews = preferences.getBoolean("disableLinkPreviews", false);
        preferSecretChats = preferences.getBoolean("preferSecretChats", false);
        deleteForAllByDefault = preferences.getBoolean("deleteForAllByDefault", false);
        stripTrackingParams = preferences.getBoolean("stripTrackingParams", false);
        disableCloudDrafts = preferences.getBoolean("disableCloudDrafts", false);
        confirmInternalLinks = preferences.getBoolean("confirmInternalLinks", false);
        showCharCounter = preferences.getBoolean("showCharCounter", false);
        quickSaveSideButtonEnabled = preferences.getBoolean("quickSaveSideButtonEnabled", false);
        quickSaveSideButtonMode = preferences.getInt("quickSaveSideButtonMode", 0);
        quickSavePresentationMode = preferences.getInt("quickSavePresentationMode", 0);
        hideJoinNotifications = preferences.getBoolean("hideJoinNotifications", false);
        autoRevealSpoilers = preferences.getBoolean("autoRevealSpoilers", false);
        activeVideoChatsToTop = preferences.getBoolean("activeVideoChatsToTop", true);
        muteAllLocally = preferences.getBoolean("muteAllLocally", false);
        prioritizeNetworkDuringCall = preferences.getBoolean("prioritizeNetworkDuringCall", true);
        callNetworkRestrictionMode = preferences.getInt("callNetworkRestrictionMode", 0);
        customMediaDownloadMode = preferences.getInt("custom_media_download_mode", 0);
        customVideoQualityPreset = preferences.getInt("custom_video_quality_preset", 1);
        customPhotoQualityPreset = preferences.getInt("custom_photo_quality_preset", 1);
        customAudioDownloadPolicy = preferences.getInt("custom_audio_download_policy", 0);
        customFileDownloadSizeLimit = preferences.getLong("custom_file_download_size_limit", 10L * 1024L * 1024L);
        customAskBeforeLargeDownload = preferences.getBoolean("custom_ask_before_large_download", true);
        customDownloadProfileMobile = preferences.getInt("custom_download_profile_mobile", 0);
        customDownloadProfileWifi = preferences.getInt("custom_download_profile_wifi", 0);
        customDownloadProfileRoaming = preferences.getInt("custom_download_profile_roaming", 0);
        customDownloadAlbumAsGroup = preferences.getBoolean("custom_download_album_as_group", false);
        customKeepOriginalMedia = preferences.getBoolean("custom_keep_original_media", false);
        customMediaAutoCleanup = preferences.getBoolean("custom_media_auto_cleanup", true);
        mgReducedTrackingExhausted = preferences.getBoolean("mgReducedTrackingExhausted", false);
    }

    public void reset() {
        sendLargePhotos = false;
        rearRoundCamera = false;
        hideChatKeyboard = false;
        hideAllTab = false;
        defaultFolderId = 0;
        messageDetailsMenu = false;
        disableLivePhotosByDefault = false;
        savedMessagesHistory = false;
        transcribeLang = SharedConfig.MG_TRANSCRIBE_LANG_DEVICE;
        hideStories = false;
        hidePremiumPromo = false;
        disableGlobalSearch = false;
        disableAiEditor = false;
        disableAiSummary = false;
        disableInstantView = false;
        disableLinkPreviews = false;
        preferSecretChats = false;
        deleteForAllByDefault = false;
        stripTrackingParams = false;
        disableCloudDrafts = false;
        confirmInternalLinks = false;
        showCharCounter = false;
        quickSaveSideButtonEnabled = false;
        quickSaveSideButtonMode = 0;
        quickSavePresentationMode = 0;
        hideJoinNotifications = false;
        autoRevealSpoilers = false;
        activeVideoChatsToTop = true;
        muteAllLocally = false;
        prioritizeNetworkDuringCall = true;
        callNetworkRestrictionMode = 0;
        customMediaDownloadMode = 0;
        customVideoQualityPreset = 1;
        customPhotoQualityPreset = 1;
        customAudioDownloadPolicy = 0;
        customFileDownloadSizeLimit = 10L * 1024L * 1024L;
        customAskBeforeLargeDownload = true;
        customDownloadProfileMobile = 0;
        customDownloadProfileWifi = 0;
        customDownloadProfileRoaming = 0;
        customDownloadAlbumAsGroup = false;
        customKeepOriginalMedia = false;
        customMediaAutoCleanup = true;
        mgReducedTrackingExhausted = false;
    }
}
