package com.example.demo.model.dto;

import java.util.List;
import java.util.UUID;

public record AskQuestionResponseDto(List<AiAnswerCardDto> answers, UUID sessionId) {
}


