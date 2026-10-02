package my.chat.chatapp.channel;

import lombok.RequiredArgsConstructor;
import my.chat.chatapp.common.AccessDeniedException;
import my.chat.chatapp.common.BadRequestException;
import my.chat.chatapp.common.NotFoundException;
import my.chat.chatapp.user.UserRepository;
import my.chat.chatapp.user.UserView;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;

    public Mono<Channel> create(String name, ChannelType type, Long creatorId) {
        if (type == null || type == ChannelType.DIRECT) {
            return Mono.error(new BadRequestException("type must be GROUP or CHANNEL"));
        }
        if (name == null || name.isBlank()) {
            return Mono.error(new BadRequestException("channel name is required"));
        }
        return channelRepository.save(Channel.builder()
                        .name(name.trim())
                        .type(type)
                        .createdBy(creatorId)
                        .createdAt(Instant.now())
                        .build())
                .flatMap(channel -> join(channel.getId(), creatorId, ChannelRole.ADMIN).thenReturn(channel));
    }

    public Mono<Channel> createOrGetDirect(Long currentUserId, Long otherUserId) {
        if (otherUserId == null) {
            return Mono.error(new BadRequestException("userId is required"));
        }
        if (otherUserId.equals(currentUserId)) {
            return Mono.error(new BadRequestException("cannot create a direct channel with yourself"));
        }
        return userRepository.findById(otherUserId)
                .switchIfEmpty(Mono.error(new NotFoundException("user not found: " + otherUserId)))
                .flatMap(user -> channelRepository.findDirectChannel(currentUserId, otherUserId)
                        .switchIfEmpty(Mono.defer(() -> createDirect(currentUserId, otherUserId))));
    }

    private Mono<Channel> createDirect(Long currentUserId, Long otherUserId) {
        return channelRepository.save(Channel.builder()
                        .type(ChannelType.DIRECT)
                        .createdBy(currentUserId)
                        .createdAt(Instant.now())
                        .build())
                .flatMap(channel -> join(channel.getId(), currentUserId, ChannelRole.MEMBER)
                        .then(join(channel.getId(), otherUserId, ChannelRole.MEMBER))
                        .thenReturn(channel));
    }

    public Mono<ChannelMember> subscribe(Long channelId, Long userId) {
        return channelRepository.findById(channelId)
                .switchIfEmpty(Mono.error(new NotFoundException("channel not found: " + channelId)))
                .flatMap(channel -> {
                    if (channel.getType() != ChannelType.CHANNEL) {
                        return Mono.error(new BadRequestException("only channels can be subscribed to"));
                    }
                    return join(channelId, userId, ChannelRole.MEMBER);
                });
    }

    public Flux<Channel> listForUser(Long userId) {
        return channelMemberRepository.findAllByUserId(userId)
                .flatMapSequential(member -> channelRepository.findById(member.getChannelId()));
    }

    public Mono<ChannelMember> addMember(Long channelId, Long callerId, Long userId) {
        if (userId == null) {
            return Mono.error(new BadRequestException("userId is required"));
        }
        return findForUser(channelId, callerId)
                .flatMap(channel -> requireChannelAdmin(channel, callerId,
                        "only channel admins can add members"))
                .then(userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new NotFoundException("user not found: " + userId))))
                .flatMap(user -> join(channelId, userId, ChannelRole.MEMBER));
    }

    public Mono<ChannelMember> promoteToAdmin(Long channelId, Long callerId, Long userId) {
        if (userId == null) {
            return Mono.error(new BadRequestException("userId is required"));
        }
        return findForUser(channelId, callerId)
                .flatMap(channel -> requireChannelAdmin(channel, callerId,
                        "only channel admins can manage admins"))
                .then(channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                        .switchIfEmpty(Mono.error(new BadRequestException("user is not a member of this channel"))))
                .flatMap(member -> {
                    member.setRole(ChannelRole.ADMIN);
                    return channelMemberRepository.save(member);
                });
    }

    public Mono<Boolean> isMember(Long channelId, Long userId) {
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }

    public Mono<Channel> findForUser(Long channelId, Long userId) {
        return channelRepository.findById(channelId)
                .switchIfEmpty(Mono.error(new NotFoundException("channel not found: " + channelId)))
                .filterWhen(channel -> channelMemberRepository.existsByChannelIdAndUserId(channelId, userId))
                .switchIfEmpty(Mono.error(new AccessDeniedException("you are not a member of this channel")));
    }

    public Mono<Channel> findForPosting(Long channelId, Long userId) {
        return findForUser(channelId, userId)
                .flatMap(channel -> requireChannelAdmin(channel, userId, "only channel admins can post"));
    }

    public Mono<ChannelView> getChannelView(Long channelId, Long userId) {
        return findForUser(channelId, userId)
                .flatMap(channel -> toView(channel, userId));
    }

    public Mono<ChannelView> toView(Channel channel, Long viewerId) {
        return channelMemberRepository.findAllByChannelId(channel.getId())
                .flatMapSequential(member -> userRepository.findById(member.getUserId()))
                .map(UserView::from)
                .collectList()
                .flatMap(members -> channelMemberRepository.findByChannelIdAndUserId(channel.getId(), viewerId)
                        .map(member -> ChannelView.from(channel, members, member.getRole()))
                        .switchIfEmpty(Mono.fromSupplier(() -> ChannelView.from(channel, members, null))));
    }

    private Mono<Channel> requireChannelAdmin(Channel channel, Long userId, String errorMessage) {
        if (channel.getType() != ChannelType.CHANNEL) {
            return Mono.just(channel);
        }
        return isChannelAdmin(channel.getId(), userId)
                .flatMap(isAdmin -> isAdmin
                        ? Mono.just(channel)
                        : Mono.error(new AccessDeniedException(errorMessage)));
    }

    private Mono<Boolean> isChannelAdmin(Long channelId, Long userId) {
        return channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                .map(member -> member.getRole() == ChannelRole.ADMIN)
                .defaultIfEmpty(false);
    }

    private Mono<ChannelMember> join(Long channelId, Long userId, ChannelRole role) {
        return channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                .switchIfEmpty(Mono.defer(() -> channelMemberRepository.save(
                        ChannelMember.builder().channelId(channelId).userId(userId).role(role).build())));
    }
}
