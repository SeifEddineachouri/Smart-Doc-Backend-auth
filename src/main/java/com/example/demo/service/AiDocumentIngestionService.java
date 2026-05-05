package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiDocumentIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiDocumentIngestionService.class);
    private static final int MAX_CONTENT_CHARS = 200_000;
    private static final String PDF_MIME = "application/pdf";

    private final RestClient aiGatewayRestClient;

    public AiDocumentIngestionService(RestClient aiGatewayRestClient) {
        this.aiGatewayRestClient = aiGatewayRestClient;
    }

    public void ingestOnUpload(UUID userId, Long documentId, MultipartFile file, String mimeType, String originalName) {
        String content = extractContent(file, mimeType, originalName, documentId);
        if (content == null || content.isBlank()) {
            return;
        }

        try {
            aiGatewayRestClient.post()
                .uri("/ingest")
                .body(new GatewayIngestRequest(userId.toString(), String.valueOf(documentId), content))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            // Best effort: keep document upload successful when AI provider is down.
            LOGGER.warn("AI ingestion skipped: gateway unavailable for userId={}, documentId={}", userId, documentId, ex);
        }
    }

    private String extractContent(MultipartFile file, String mimeType, String originalName, Long documentId) {
        if (file == null || file.isEmpty()) {
            LOGGER.info("AI ingestion skipped: empty file for documentId={}", documentId);
            return null;
        }

        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (PDF_MIME.equals(normalizedMime)) {
            return truncateIfNeeded(extractPdfText(file, documentId), documentId);
        }

        if (!isTextLike(normalizedMime)) {
            LOGGER.info("AI ingestion skipped: unsupported mimeType={} for documentId={}, file={}", mimeType, documentId, originalName);
            return null;
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            return truncateIfNeeded(content, documentId);
        } catch (IOException ex) {
            LOGGER.warn("AI ingestion skipped: failed to read content for documentId={}", documentId, ex);
            return null;
        }
    }

    private String extractPdfText(MultipartFile file, Long documentId) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(document).trim();
        } catch (IOException ex) {
            LOGGER.warn("AI ingestion skipped: failed to extract PDF text for documentId={}", documentId, ex);
            return null;
        }
    }

    private String truncateIfNeeded(String content, Long documentId) {
        if (content == null || content.isBlank()) {
            return null;
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            LOGGER.info("AI ingestion content truncated for documentId={} to {} characters", documentId, MAX_CONTENT_CHARS);
            return content.substring(0, MAX_CONTENT_CHARS);
        }
        return content;
    }

    private boolean isTextLike(String mimeType) {
        return mimeType.startsWith("text/")
            || "application/json".equals(mimeType)
            || "application/xml".equals(mimeType)
            || "application/x-yaml".equals(mimeType)
            || "application/javascript".equals(mimeType)
            || "application/csv".equals(mimeType)
            || "text/csv".equals(mimeType);
    }

    private record GatewayIngestRequest(String userId, String documentId, String content) {
    }
}
