package my.chat.chatapp.chat;

import my.chat.chatapp.auth.AuthResponse;
import my.chat.chatapp.channel.ChannelMemberRepository;
import my.chat.chatapp.channel.ChannelRepository;
import my.chat.chatapp.channel.ChannelRole;
import my.chat.chatapp.channel.ChannelType;
import my.chat.chatapp.channel.ChannelView;
import my.chat.chatapp.common.ErrorView;
import my.chat.chatapp.message.MessageRepository;
import my.chat.chatapp.message.MessageView;
import my.chat.chatapp.user.UserRepository;
import my.chat.chatapp.user.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@AutoConfigureWebTestClient
@Disabled("DB-backed tests postponed: H2/Postgres test infra under discussion")
class ChatFlowTest {

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    ChannelMemberRepository channelMemberRepository;

    @Autowired
    MessageRepository messageRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        messageRepository.deleteAll().block();
        channelMemberRepository.deleteAll().block();
        channelRepository.deleteAll().block();
        userRepository.deleteAll().block();
    }

    @Test
    void directChannelIsCreatedOncePerPair() {
        AuthResponse alice = register("alice");
        AuthResponse bob = register("bob");

        ChannelView first = createDirect(alice, bob.user().id());
        assertEquals(ChannelType.DIRECT, first.type());
        assertNull(first.name());
        assertEquals(Set.of("alice", "bob"), usernames(first));

        ChannelView asOtherUser = createDirect(bob, alice.user().id());
        assertEquals(first.id(), asOtherUser.id());

        ChannelView repeated = createDirect(alice, bob.user().id());
        assertEquals(first.id(), repeated.id());

        assertEquals(1, channelRepository.findAll()
                .filter(channel -> channel.getType() == ChannelType.DIRECT).count().block());
        assertEquals(2, channelMemberRepository.count().block());
    }

    @Test
    void groupChannelMembersAndPagedHistory() {
        AuthResponse alice = register("alice");
        AuthResponse bob = register("bob");

        ChannelView group = createGroup(alice, "Study group");
        assertEquals("Study group", group.name());
        assertEquals(ChannelType.GROUP, group.type());
        assertEquals(List.of("alice"), usernames(group));

        webTestClient.get().uri("/api/channels/" + group.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .exchange()
                .expectStatus().isOk();

        ChannelView updated = addMember(alice, group.id(), bob.user().id());
        assertEquals(Set.of("alice", "bob"), usernames(updated));
        assertEquals(2, channelMemberRepository.count().block());

        webTestClient.get().uri("/api/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChannelView[].class)
                .consumeWith(result -> {
                    ChannelView[] channels = result.getResponseBody();
                    assertNotNull(channels);
                    assertEquals(1, channels.length);
                    assertEquals("Study group", channels[0].name());
                });

        for (int i = 1; i <= 5; i++) {
            sendMessage(bob, group.id(), "msg-" + i);
        }

        assertEquals(List.of("msg-1", "msg-2", "msg-3", "msg-4", "msg-5"),
                texts(history(alice, group.id(), 0, 50)));
        assertEquals(List.of("msg-1", "msg-2"), texts(history(alice, group.id(), 0, 2)));
        assertEquals(List.of("msg-3", "msg-4"), texts(history(alice, group.id(), 1, 2)));
        assertEquals(List.of("msg-5"), texts(history(alice, group.id(), 2, 2)));

        MessageView[] page = history(alice, group.id(), 0, 2);
        assertEquals("bob", page[0].author().username());
        assertEquals(group.id(), page[0].channelId());
        assertNotNull(page[0].sentAt());
    }

    @Test
    void nonMemberIsForbiddenToReadAndWrite() {
        AuthResponse alice = register("alice");
        AuthResponse bob = register("bob");
        AuthResponse carol = register("carol");

        ChannelView group = createGroup(alice, "Private club");

        ErrorView error = webTestClient.post().uri("/api/channels/" + group.id() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"hello\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(ErrorView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(error);
        assertEquals("forbidden", error.code());
        assertNotNull(error.message());

        webTestClient.get().uri("/api/channels/" + group.id() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.token())
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.get().uri("/api/channels/" + group.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.token())
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.post().uri("/api/channels/" + group.id() + "/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":" + bob.user().id() + "}")
                .exchange()
                .expectStatus().isForbidden();

        sendMessage(alice, group.id(), "still works");
    }

    @Test
    void missingEntitiesReturnNotFound() {
        AuthResponse alice = register("alice");

        ErrorView error = webTestClient.get().uri("/api/channels/999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(error);
        assertEquals("not_found", error.code());

        webTestClient.post().uri("/api/channels/999999/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"hi\"}")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post().uri("/api/channels/direct")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":999999}")
                .exchange()
                .expectStatus().isNotFound();

        ChannelView group = createGroup(alice, "group");
        webTestClient.post().uri("/api/channels/" + group.id() + "/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":999999}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void invalidRequestsReturnBadRequest() {
        AuthResponse alice = register("alice");

        ErrorView error = webTestClient.post().uri("/api/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"  \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(error);
        assertEquals("bad_request", error.code());

        webTestClient.post().uri("/api/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"x\",\"type\":\"DIRECT\"}")
                .exchange()
                .expectStatus().isBadRequest();

        ChannelView group = createGroup(alice, "group");

        webTestClient.post().uri("/api/channels/" + group.id() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.post().uri("/api/channels/direct")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":" + alice.user().id() + "}")
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.get().uri("/api/channels/" + group.id() + "/messages?size=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.post().uri("/api/channels/" + group.id() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void channelPostingIsLimitedToAdmins() {
        AuthResponse alice = register("alice");
        AuthResponse bob = register("bob");
        AuthResponse carol = register("carol");

        ChannelView channel = createChannel(alice, "Announcements");
        assertEquals(ChannelType.CHANNEL, channel.type());
        assertEquals(ChannelRole.ADMIN, channel.role());
        assertEquals(List.of("alice"), usernames(channel));

        ChannelView asBob = subscribe(bob, channel.id());
        assertEquals(ChannelRole.MEMBER, asBob.role());
        assertEquals(Set.of("alice", "bob"), usernames(asBob));

        webTestClient.post().uri("/api/channels/" + channel.id() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"hi\"}")
                .exchange()
                .expectStatus().isForbidden();

        sendMessage(alice, channel.id(), "hello subscribers");
        assertEquals(List.of("hello subscribers"), texts(history(bob, channel.id(), 0, 10)));

        webTestClient.get().uri("/api/channels/" + channel.id() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.token())
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.post().uri("/api/channels/" + channel.id() + "/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":" + carol.user().id() + "}")
                .exchange()
                .expectStatus().isForbidden();

        ChannelView withCarol = addMember(alice, channel.id(), carol.user().id());
        assertEquals(ChannelRole.MEMBER, withCarol.role());

        promoteToAdmin(alice, channel.id(), bob.user().id());
        sendMessage(bob, channel.id(), "now I can post too");
        assertEquals(List.of("hello subscribers", "now I can post too"),
                texts(history(carol, channel.id(), 0, 10)));
    }

    @Test
    void subscribeIsOnlyForChannels() {
        AuthResponse alice = register("alice");
        AuthResponse bob = register("bob");

        ChannelView group = createGroup(alice, "Study group");
        webTestClient.post().uri("/api/channels/" + group.id() + "/subscribe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.token())
                .exchange()
                .expectStatus().isBadRequest();

        ChannelView direct = createDirect(alice, bob.user().id());
        webTestClient.post().uri("/api/channels/" + direct.id() + "/subscribe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .exchange()
                .expectStatus().isBadRequest();
    }

    private AuthResponse register(String username) {
        AuthResponse response = webTestClient.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + username + "\",\"password\":\"secret123\",\"displayName\":\"" + username + "\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(response);
        return response;
    }

    private ChannelView createGroup(AuthResponse caller, String name) {
        ChannelView view = webTestClient.post().uri("/api/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"" + name + "\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ChannelView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(view);
        return view;
    }

    private ChannelView createDirect(AuthResponse caller, long otherUserId) {
        ChannelView view = webTestClient.post().uri("/api/channels/direct")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":" + otherUserId + "}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ChannelView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(view);
        return view;
    }

    private ChannelView createChannel(AuthResponse caller, String name) {
        ChannelView view = webTestClient.post().uri("/api/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"" + name + "\",\"type\":\"CHANNEL\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ChannelView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(view);
        return view;
    }

    private ChannelView subscribe(AuthResponse caller, long channelId) {
        ChannelView view = webTestClient.post().uri("/api/channels/" + channelId + "/subscribe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChannelView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(view);
        return view;
    }

    private ChannelView promoteToAdmin(AuthResponse caller, long channelId, long userId) {
        ChannelView view = webTestClient.post().uri("/api/channels/" + channelId + "/admins")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":" + userId + "}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChannelView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(view);
        return view;
    }

    private ChannelView addMember(AuthResponse caller, long channelId, long userId) {
        ChannelView view = webTestClient.post().uri("/api/channels/" + channelId + "/members")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userId\":" + userId + "}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChannelView.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(view);
        return view;
    }

    private void sendMessage(AuthResponse sender, long channelId, String text) {
        webTestClient.post().uri("/api/channels/" + channelId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sender.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"" + text + "\"}")
                .exchange()
                .expectStatus().isCreated();
    }

    private MessageView[] history(AuthResponse caller, long channelId, int page, int size) {
        MessageView[] messages = webTestClient.get().uri(
                        "/api/channels/" + channelId + "/messages?page=" + page + "&size=" + size)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MessageView[].class)
                .returnResult()
                .getResponseBody();
        assertNotNull(messages);
        return messages;
    }

    private Set<String> usernames(ChannelView view) {
        return view.members().stream().map(UserView::username).collect(Collectors.toSet());
    }

    private List<String> texts(MessageView[] messages) {
        return Arrays.stream(messages).map(MessageView::text).toList();
    }
}
