package com.example.demo.controller;

import com.example.demo.model.dto.AskQuestionRequestDto;
import com.example.demo.model.dto.AskQuestionResponseDto;
import com.example.demo.model.dto.ChatHistoryResponseDto;
import com.example.demo.service.AiQaService;
import com.example.demo.util.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiQaService aiQaService;

    public AiController(AiQaService aiQaService) {
        this.aiQaService = aiQaService;
    }

    @PostMapping("/questions")
    public ResponseEntity<AskQuestionResponseDto> askQuestion(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody AskQuestionRequestDto request,
        @RequestParam(name = "sessionId", required = false) UUID sessionId
    ) {
        return ResponseEntity.ok(aiQaService.askQuestion(principal.getUser().getId(), request, sessionId));
    }

    @GetMapping("/history")
    public ResponseEntity<ChatHistoryResponseDto> history(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(name = "sessionId", required = false) UUID sessionId
    ) {
        return ResponseEntity.ok(aiQaService.getHistory(principal.getUser().getId(), page, size, sessionId));
    }
}


