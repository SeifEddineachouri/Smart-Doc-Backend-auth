package com.example.demo.service;

import com.example.demo.config.AiGatewayProperties;
import com.example.demo.model.dto.AiAnswerCardDto;
import com.example.demo.model.dto.AskQuestionRequestDto;
import com.example.demo.model.dto.AskQuestionResponseDto;
import com.example.demo.model.dto.ChatHistoryItemDto;
import com.example.demo.model.dto.ChatHistoryResponseDto;
import com.example.demo.model.entity.ChatMessageEntity;
import com.example.demo.model.entity.ChatSessionEntity;
import com.example.demo.model.entity.DocumentEntity;
import com.example.demo.model.entity.UserEntity;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.DocumentRepository;
import com.example.demo.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiQaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiQaService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final RestClient aiGatewayRestClient;
    private final AiGatewayProperties aiGatewayProperties;
    private final ChatSessionService chatSessionService;
    private final AuditEventPublisher auditEventPublisher;

    public AiQaService(
        ChatMessageRepository chatMessageRepository,
        UserRepository userRepository,
        DocumentRepository documentRepository,
        RestClient aiGatewayRestClient,
        AiGatewayProperties aiGatewayProperties,
        ChatSessionService chatSessionService,
        AuditEventPublisher auditEventPublisher
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.aiGatewayRestClient = aiGatewayRestClient;
        this.aiGatewayProperties = aiGatewayProperties;
        this.chatSessionService = chatSessionService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public AskQuestionResponseDto askQuestion(UUID userId, AskQuestionRequestDto request, UUID sessionId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));

        ChatSessionEntity session = chatSessionService.resolveSession(userId, sessionId);
        validateDocuments(user, session, request);

        auditEventPublisher.aiQuestionRequested(userId, session.getId(), request.documents().size(), request.question().length());

        LOGGER.info(
            "audit event=ai_question_requested userId={} sessionId={} documentCount={} questionChars={}",
            userId,
            session.getId(),
            request.documents().size(),
            request.question().length()
        );

        List<String> documentIds = request.documents().stream()
            .map(document -> String.valueOf(document.id()))
            .toList();

        GatewayAskQuestionResponse gatewayResponse;
        try {
            gatewayResponse = aiGatewayRestClient.post()
                .uri(aiGatewayProperties.getAskPath())
                .body(new GatewayAskQuestionRequest(userId.toString(), request.question(), documentIds))
                .retrieve()
                .body(GatewayAskQuestionResponse.class);
        } catch (RestClientException ex) {
            LOGGER.warn("AI question failed: gateway unavailable for userId={}, questionLength={}", userId, request.question().length(), ex);
            auditEventPublisher.aiQuestionFailed(userId, session.getId(), request.documents().size(), "gateway-unavailable", ex.getClass().getSimpleName());
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI service is temporarily unavailable. Please retry in a moment.",
                ex
            );
        }

        String answerSummary = gatewayResponse != null && gatewayResponse.answer() != null
            ? gatewayResponse.answer()
            : "No answer returned by AI gateway.";

        LOGGER.info(
            "audit event=ai_question_completed userId={} sessionId={} answerChars={} status=ok",
            userId,
            session.getId(),
            answerSummary.length()
        );

        List<AiAnswerCardDto> answers = List.of(new AiAnswerCardDto("AI Answer", "Summary", answerSummary));
        auditEventPublisher.aiQuestionCompleted(userId, session.getId(), request.documents().size(), answerSummary.length());

        ChatMessageEntity chatMessage = new ChatMessageEntity();
        chatMessage.setUser(user);
        chatMessage.setSession(session);
        chatMessage.setQuestion(request.question());
        chatMessage.setAnswer(answerSummary);
        chatMessageRepository.save(chatMessage);
        chatSessionService.touchSession(session);

        return new AskQuestionResponseDto(answers, session.getId());
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponseDto getHistory(UUID userId, int page, int size, UUID sessionId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));

        ChatSessionEntity session = chatSessionService.resolveSession(userId, sessionId);

        LOGGER.info(
            "audit event=ai_history_requested userId={} sessionId={} page={} size={}",
            userId,
            session.getId(),
            page,
            size
        );

        Page<ChatMessageEntity> historyPage = chatMessageRepository.findAllByUserAndSessionOrderByCreatedAtDesc(
            user,
            session,
            PageRequest.of(page, size)
        );

        List<ChatHistoryItemDto> items = historyPage.getContent().stream()
            .map(item -> new ChatHistoryItemDto(item.getId(), item.getQuestion(), item.getAnswer(), item.getCreatedAt()))
            .toList();

        return new ChatHistoryResponseDto(
            items,
            historyPage.getNumber(),
            historyPage.getSize(),
            historyPage.getTotalElements(),
            historyPage.getTotalPages(),
            session.getId()
        );
    }

    private void validateDocuments(UserEntity user, ChatSessionEntity session, AskQuestionRequestDto request) {
        List<Long> requestedIds = request.documents().stream()
            .map(document -> document.id())
            .toList();

        Set<Long> requestedSet = requestedIds.stream().collect(Collectors.toSet());
        List<DocumentEntity> owned = documentRepository.findAllByUserAndSessionAndIdIn(user, session, requestedIds);
        Set<Long> ownedIds = owned.stream().map(DocumentEntity::getId).collect(Collectors.toSet());

        if (ownedIds.size() != requestedSet.size()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "One or more documents do not belong to this session");
        }
    }

    private record GatewayAskQuestionRequest(String userId, String question, List<String> documentIds) {
    }

    private record GatewayAskQuestionResponse(String answer) {
    }
}


