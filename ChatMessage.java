

import java.io.Serializable;

enum ChatMessageType {
    MESSAGE, WHOIS, LOGOUT, PINGUFACT
}
public class ChatMessage  implements Serializable {
    private ChatMessageType type;
    private String message;

    public ChatMessage(ChatMessageType type, String message) {
        this.type = type;
        this.message = message;
    }

    public ChatMessageType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
