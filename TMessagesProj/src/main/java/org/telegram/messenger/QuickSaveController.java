package org.telegram.messenger;

import java.util.ArrayList;

/** Shared Quick Save forwarding path for message-menu and message-row entry points. */
public final class QuickSaveController {
    public interface Callback {
        void onSaved(int messageCount);
        void onError(int result);
    }

    private QuickSaveController() {
    }

    public static ArrayList<MessageObject> resolveMessages(MessageObject message, MessageObject.GroupedMessages group) {
        ArrayList<MessageObject> result = new ArrayList<>();
        if (group != null && group.messages != null && !group.messages.isEmpty()) {
            result.addAll(group.messages);
        } else if (message != null) {
            result.add(message);
        }
        return result;
    }

    public static void save(int account, ArrayList<MessageObject> messages, long savedMessagesDialogId, Callback callback) {
        if (messages == null || messages.isEmpty()) {
            if (callback != null) {
                callback.onError(-1);
            }
            return;
        }
        int result = SendMessagesHelper.getInstance(account).sendMessage(
            messages,
            savedMessagesDialogId,
            false,
            false,
            true,
            0,
            0,
            null,
            -1,
            0,
            0,
            null
        );
        if (callback == null) {
            return;
        }
        if (result == 0) {
            callback.onSaved(messages.size());
        } else {
            callback.onError(result);
        }
    }
}
