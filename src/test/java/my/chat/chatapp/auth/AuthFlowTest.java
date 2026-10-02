package my.chat.chatapp.auth;

import my.chat.chatapp.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@Disabled("DB-backed tests postponed: H2/Postgres test infra under discussion")
class AuthFlowTest {

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll().block();
    }

    @Test
    void fullAuthFlow() {
        webTestClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\",\"displayName\":\"Alice\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.token").exists()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.user.username").isEqualTo("alice")
                .jsonPath("$.user.displayName").isEqualTo("Alice");

        webTestClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"other-pass\",\"displayName\":\"Alice Again\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        AuthResponse auth = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"secret123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();
        assert auth != null && auth.token() != null;

        webTestClient.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("alice");

        webTestClient.get().uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"alice\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
