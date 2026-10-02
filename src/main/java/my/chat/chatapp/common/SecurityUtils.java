package my.chat.chatapp.common;

import my.chat.chatapp.user.User;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Mono<Long> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> (User) authentication.getPrincipal())
                .map(User::getId);
    }
}
