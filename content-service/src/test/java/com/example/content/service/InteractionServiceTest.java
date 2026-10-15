package com.example.content.service;

import com.example.content.dto.InteractionRequest;
import com.example.content.entity.Interaction;
import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
import com.example.content.event.ContentInteractionEvent;
import com.example.content.repository.InteractionRepository;
import com.example.content.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    @Mock
    private InteractionRepository interactionRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InteractionService interactionService;

    @Test
    void publishesLikeWithStableIdAndPostOwnerRecipient() {
        UUID postId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID postOwnerId = UUID.randomUUID();
        UUID interactionId = UUID.randomUUID();
        Post post = new Post();
        post.setAuthorId(postOwnerId);
        when(postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)).thenReturn(Optional.of(post));
        when(interactionRepository.existsByUserIdAndPostIdAndType(eq(actorId), eq(postId), any()))
                .thenReturn(false);
        when(interactionRepository.save(any(Interaction.class))).thenAnswer(invocation -> {
            Interaction saved = invocation.getArgument(0);
            saved.setId(interactionId);
            return saved;
        });

        InteractionRequest request = new InteractionRequest();
        request.setType("LIKE");
        interactionService.interact(postId, actorId, request);

        ArgumentCaptor<ContentInteractionEvent> eventCaptor = ArgumentCaptor.forClass(ContentInteractionEvent.class);
        verify(kafkaTemplate).send(eq("content.interaction"), eq(postId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(interactionId.toString());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(actorId.toString());
        assertThat(eventCaptor.getValue().getTargetUserId()).isEqualTo(postOwnerId.toString());
        assertThat(eventCaptor.getValue().getPostId()).isEqualTo(postId.toString());
    }
}
