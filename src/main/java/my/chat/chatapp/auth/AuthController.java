package my.chat.chatapp.auth;

import lombok.RequiredArgsConstructor;
import my.chat.chatapp.security.JwtService;
import my.chat.chatapp.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@RequestBody RegisterRequest request) {
        return userService.register(request.username(), request.password(), request.displayName())
                .map(user -> AuthResponse.of(user, jwtService.generateToken(user)));
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody LoginRequest request) {
        return userService.authenticate(request.username(), request.password())
                .map(user -> AuthResponse.of(user, jwtService.generateToken(user)));
    }
}
