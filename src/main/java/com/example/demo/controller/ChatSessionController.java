package com.example.demo.controller;

import com.example.demo.model.dto.ChatSessionDto;
import com.example.demo.model.dto.CreateChatSessionRequestDto;
import com.example.demo.service.ChatSessionService;
import com.example.demo.util.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public ResponseEntity<ChatSessionDto> createSession(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody(required = false) CreateChatSessionRequestDto request
    ) {
        String name = request == null ? null : request.name();
        return ResponseEntity.ok(chatSessionService.createSession(principal.getUser().getId(), name));
    }

    @GetMapping
    public ResponseEntity<List<ChatSessionDto>> listSessions(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(chatSessionService.listSessions(principal.getUser().getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        chatSessionService.deleteSession(principal.getUser().getId(), id);
        return ResponseEntity.noContent().build();
    }
}
