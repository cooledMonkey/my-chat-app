package my.chat.chatapp.message;

import lombok.RequiredArgsConstructor;
import my.chat.chatapp.common.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageView> send(@PathVariable Long channelId, @RequestBody MessageSendRequest request) {
        return SecurityUtils.currentUserId()
                .flatMap(userId -> messageService.send(channelId, userId, request.text()));
    }

    @GetMapping
    public Flux<MessageView> history(@PathVariable Long channelId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return SecurityUtils.currentUserId()
                .flatMapMany(userId -> messageService.history(channelId, userId, page, size));
    }
}
