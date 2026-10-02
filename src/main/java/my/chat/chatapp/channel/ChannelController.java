package my.chat.chatapp.channel;

import lombok.RequiredArgsConstructor;
import my.chat.chatapp.common.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping
    public Flux<ChannelView> list() {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> channelService.listForUser(userId)
                        .flatMapSequential(channel -> channelService.toView(channel, userId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChannelView> create(@RequestBody ChannelCreateRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.create(request.name(), request.type(), userId)
                        .flatMap(channel -> channelService.getChannelView(channel.getId(), userId)));
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChannelView> createDirect(@RequestBody DirectChannelRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.createOrGetDirect(userId, request.userId())
                        .flatMap(channel -> channelService.getChannelView(channel.getId(), userId)));
    }

    @GetMapping("/{id}")
    public Mono<ChannelView> get(@PathVariable Long id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.getChannelView(id, userId));
    }

    @PostMapping("/{id}/members")
    public Mono<ChannelView> addMember(@PathVariable Long id, @RequestBody AddMemberRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.addMember(id, userId, request.userId())
                        .then(channelService.getChannelView(id, userId)));
    }

    @PostMapping("/{id}/subscribe")
    public Mono<ChannelView> subscribe(@PathVariable Long id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.subscribe(id, userId)
                        .then(channelService.getChannelView(id, userId)));
    }

    @PostMapping("/{id}/admins")
    public Mono<ChannelView> promoteToAdmin(@PathVariable Long id, @RequestBody AddMemberRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.promoteToAdmin(id, userId, request.userId())
                        .then(channelService.getChannelView(id, userId)));
    }
}
