package my.chat.chatapp.channel;

import my.chat.chatapp.user.UserView;

import java.util.List;

public record ChannelView(Long id, String name, ChannelType type, List<UserView> members) {

    public static ChannelView from(Channel channel, List<UserView> members) {
        return new ChannelView(channel.getId(), channel.getName(), channel.getType(), members);
    }
}
