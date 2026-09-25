package my.chat.chatapp.auth;

import my.chat.chatapp.user.User;
import my.chat.chatapp.user.UserView;

public record AuthResponse(String token, String tokenType, UserView user) {

    public static AuthResponse of(User user, String token) {
        return new AuthResponse(token, "Bearer", UserView.from(user));
    }
}
