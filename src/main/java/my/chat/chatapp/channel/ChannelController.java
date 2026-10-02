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
                .flatMapMany(channelService::listForUser)
                .flatMapSequential(channelService::toView);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChannelView> createGroup(@RequestBody ChannelCreateRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.createGroup(request.name(), userId))
                .flatMap(channelService::toView);
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChannelView> createDirect(@RequestBody DirectChannelRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.createOrGetDirect(userId, request.userId()))
                .flatMap(channelService::toView);
    }

    @GetMapping("/{id}")
    public Mono<ChannelView> get(@PathVariable Long id) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.getChannelView(id, userId));
    }

    @PostMapping("/{id}/members")
    public Mono<ChannelView> addMember(@PathVariable Long id, @RequestBody AddMemberRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> channelService.findForUser(id, userId)
                        .then(channelService.addMember(id, request.userId()))
                        .then(channelService.getChannelView(id, userId)));
    }
}
