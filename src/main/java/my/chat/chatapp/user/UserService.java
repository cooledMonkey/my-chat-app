package my.chat.chatapp.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<User> register(String username, String password, String displayName) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()
                || displayName == null || displayName.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "username, password and displayName are required"));
        }
        return userRepository.findByUsername(username)
                .flatMap(existing -> Mono.<User>error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "username already taken")))
                .switchIfEmpty(Mono.defer(() -> userRepository.save(User.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(password))
                        .displayName(displayName)
                        .createdAt(Instant.now())
                        .build())));
    }

    public Mono<User> authenticate(String username, String password) {
        return findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "invalid credentials")));
    }

    public Mono<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
