package my.chat.chatapp.message;

import my.chat.chatapp.user.User;
import my.chat.chatapp.user.UserView;

import java.time.Instant;

public record MessageView(Long id, Long channelId, UserView author, String text, Instant sentAt) {

    public static MessageView from(Message message, User author) {
        return new MessageView(message.getId(), message.getChannelId(), UserView.from(author),
                message.getText(), message.getSentAt());
    }
}
