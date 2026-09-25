package my.chat.chatapp.user;

public record UserView(Long id, String username, String displayName) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
