package com.example.chat.service;

import com.example.chat.client.AuthServiceClient;
import com.example.chat.dto.ConversationResponse;
import com.example.chat.dto.MessagePageResponse;
import com.example.chat.dto.MessageResponse;
import com.example.chat.dto.SendMessageRequest;
import com.example.chat.entity.Conversation;
import com.example.chat.entity.ConversationMember;
import com.example.chat.entity.Message;
import com.example.chat.exception.AccessDeniedException;
import com.example.chat.exception.NotFoundException;
import com.example.chat.messaging.ChatEventPublisher;
import com.example.chat.repository.ConversationMemberRepository;
import com.example.chat.repository.ConversationReadStatusRepository;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMemberRepository memberRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ConversationReadStatusRepository readStatusRepository;
    @Mock private AuthServiceClient authServiceClient;
    @Mock private ChatEventPublisher eventPublisher;
    @Mock private ChatRealtimeService realtimeService;

    private ChatService chatService;

    private final UUID me = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
                conversationRepository,
                memberRepository,
                messageRepository,
                readStatusRepository,
                authServiceClient,
                eventPublisher,
                realtimeService
        );
    }

    private Conversation conversation() {
        return Conversation.builder()
                .id(conversationId)
                .type("DIRECT")
                .createdBy(me)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ConversationMember member(UUID userId) {
        return ConversationMember.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role("MEMBER")
                .build();
    }

    @Test
    @DisplayName("Tạo hội thoại DIRECT: trả về hội thoại đã có thay vì tạo trùng")
    void reusesExistingDirectConversation() {
        when(conversationRepository.findDirectConversation(me, partner))
                .thenReturn(List.of(conversation()));
        when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId))
                .thenReturn(List.of(member(me), member(partner)));
        when(messageRepository.findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId))
                .thenReturn(Optional.empty());
        when(readStatusRepository.findById(any())).thenReturn(Optional.empty());
        when(messageRepository.countUnreadAll(eq(conversationId), eq(me))).thenReturn(0L);
        when(authServiceClient.getUsersSummaryMap(anyList())).thenReturn(Collections.emptyMap());

        ConversationResponse response = chatService.getOrCreateDirectConversation(me, partner);

        assertThat(response.getId()).isEqualTo(conversationId.toString());
        assertThat(response.getPartner().getId()).isEqualTo(partner.toString());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Không thể tạo hội thoại với chính mình")
    void rejectsSelfConversation() {
        assertThatThrownBy(() -> chatService.getOrCreateDirectConversation(me, me))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Enrichment lấy đúng username/avatar của người đối thoại")
    void enrichesPartnerFromAuthService() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, me)).thenReturn(true);
        when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId))
                .thenReturn(List.of(member(me), member(partner)));
        when(messageRepository.findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId))
                .thenReturn(Optional.empty());
        when(readStatusRepository.findById(any())).thenReturn(Optional.empty());
        when(messageRepository.countUnreadAll(eq(conversationId), eq(me))).thenReturn(0L);

        AuthServiceClient.UserSummary summary = new AuthServiceClient.UserSummary();
        summary.setId(partner.toString());
        summary.setUsername("hquy.dev");
        summary.setDisplayName("Huy");
        summary.setAvatarUrl("/a.jpg");
        summary.setVerified(true);
        when(authServiceClient.getUsersSummaryMap(anyList())).thenReturn(Map.of(partner, summary));

        ConversationResponse response = chatService.getConversation(conversationId, me);

        assertThat(response.getPartner().getUsername()).isEqualTo("hquy.dev");
        assertThat(response.getPartner().getAvatarUrl()).isEqualTo("/a.jpg");
        assertThat(response.getPartner().getVerified()).isTrue();
    }

    @Test
    @DisplayName("Người ngoài hội thoại bị từ chối (403, không phải 500)")
    void rejectsNonMember() {
        UUID stranger = UUID.randomUUID();
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, stranger))
                .thenReturn(false);

        assertThatThrownBy(() -> chatService.getConversationMessages(conversationId, stranger, null, 0, 30))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Gửi tin: lưu DB, broadcast realtime và publish Kafka")
    void sendMessageBroadcastsAndPublishes() {
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, me)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId))
                .thenReturn(List.of(member(me), member(partner)));

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("  xin chào  ");

        MessageResponse response = chatService.sendMessage(conversationId, me, request);

        assertThat(response.getContent()).isEqualTo("xin chào");
        verify(realtimeService).broadcastMessage(any(MessageResponse.class));
        verify(eventPublisher).publishMessageCreated(any(Message.class), eq(me), anyList());
    }

    @Test
    @DisplayName("Không gửi được tin rỗng")
    void rejectsEmptyMessage() {
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, me)).thenReturn(true);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("   ");

        assertThatThrownBy(() -> chatService.sendMessage(conversationId, me, request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Chỉ người gửi mới xoá được tin nhắn")
    void onlySenderCanDelete() {
        UUID messageId = UUID.randomUUID();
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, partner)).thenReturn(true);
        Message message = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .senderId(me)
                .createdAt(Instant.now())
                .build();
        when(messageRepository.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.deleteMessage(conversationId, messageId, partner))
                .isInstanceOf(AccessDeniedException.class);
        verify(realtimeService, never()).broadcastMessageDeleted(any(), any());
    }

    @Test
    @DisplayName("Lấy tin nhắn trả về thứ tự cũ → mới cho UI")
    void messagesReturnedInAscendingOrder() {
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, me)).thenReturn(true);

        Message newer = Message.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .senderId(partner).content("mới").createdAt(Instant.now()).build();
        Message older = Message.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .senderId(me).content("cũ").createdAt(Instant.now().minusSeconds(60)).build();

        Page<Message> page = new PageImpl<>(List.of(newer, older), PageRequest.of(0, 30), 2);
        when(messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(eq(conversationId), any()))
                .thenReturn(page);

        MessagePageResponse response = chatService.getConversationMessages(conversationId, me, null, 0, 30);

        assertThat(response.getContent()).extracting(MessageResponse::getContent)
                .containsExactly("cũ", "mới");
    }

    @Test
    @DisplayName("Đánh dấu đã đọc lưu đúng lastReadMessageId")
    void markReadPersistsCursor() {
        UUID messageId = UUID.randomUUID();
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, me)).thenReturn(true);
        Message message = Message.builder().id(messageId).conversationId(conversationId)
                .senderId(partner).createdAt(Instant.now()).build();
        when(messageRepository.findByIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message));
        when(readStatusRepository.findById(any())).thenReturn(Optional.empty());
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId))
                .thenReturn(List.of(member(me), member(partner)));
        when(messageRepository.findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId))
                .thenReturn(Optional.of(message));
        when(messageRepository.countUnreadAll(eq(conversationId), eq(me))).thenReturn(0L);
        when(authServiceClient.getUsersSummaryMap(anyList())).thenReturn(Collections.emptyMap());

        chatService.markRead(conversationId, me, messageId);

        verify(readStatusRepository).save(any());
        verify(realtimeService).broadcastReadReceipt(eq(conversationId), eq(me), eq(messageId), any());
    }

    @Test
    @DisplayName("Hội thoại không tồn tại trả 404")
    void missingConversationReturnsNotFound() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getConversation(conversationId, me))
                .isInstanceOf(NotFoundException.class);
    }
}
