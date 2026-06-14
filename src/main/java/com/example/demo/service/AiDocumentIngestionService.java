package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiDocumentIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiDocumentIngestionService.class);
    private static final int MAX_CONTENT_CHARS = 200_000;
    private static final String PDF_MIME = "application/pdf";

    private final RestClient aiGatewayRestClient;

    public AiDocumentIngestionService(RestClient aiGatewayRestClient) {
        this.aiGatewayRestClient = aiGatewayRestClient;
    }

    /**
     * Ingests an uploaded document into the AI gateway in the background.
     *
     * <p>Runs on the {@code aiIngestionExecutor} pool so the upload HTTP response
     * returns as soon as the file is saved to disk and its metadata is committed.
     * PDF text extraction and the multi-hop embedding call no longer block the
     * request thread. The file is read from its persisted {@code storagePath}
     * rather than the request-scoped {@link org.springframework.web.multipart.MultipartFile},
     * whose backing store may be reclaimed once the request completes.
     */
    @Async("aiIngestionExecutor")
    public void ingestOnUpload(UUID userId, Long documentId, Path storagePath, String mimeType, String originalName) {
        String content = extractContent(storagePath, mimeType, originalName, documentId);
        if (content == null || content.isBlank()) {
            return;
        }

        try {
            aiGatewayRestClient.post()
                .uri("/ingest")
                .body(new GatewayIngestRequest(userId.toString(), String.valueOf(documentId), content))
                .retrieve()
                .toBodilessEntity();
            LOGGER.info("AI ingestion completed for userId={}, documentId={}", userId, documentId);
        } catch (RestClientException ex) {
            // Best effort: keep document upload successful when AI provider is down.
            LOGGER.warn("AI ingestion skipped: gateway unavailable for userId={}, documentId={}", userId, documentId, ex);
        }
    }

    private String extractContent(Path storagePath, String mimeType, String originalName, Long documentId) {
        if (storagePath == null || !Files.isReadable(storagePath)) {
            LOGGER.info("AI ingestion skipped: file not readable for documentId={}, path={}", documentId, storagePath);
            return null;
        }

        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (PDF_MIME.equals(normalizedMime)) {
            return truncateIfNeeded(extractPdfText(storagePath, documentId), documentId);
        }

        if (!isTextLike(normalizedMime)) {
            LOGGER.info("AI ingestion skipped: unsupported mimeType={} for documentId={}, file={}", mimeType, documentId, originalName);
            return null;
        }

        try {
            String content = Files.readString(storagePath, StandardCharsets.UTF_8).trim();
            return truncateIfNeeded(content, documentId);
        } catch (IOException ex) {
            LOGGER.warn("AI ingestion skipped: failed to read content for documentId={}", documentId, ex);
            return null;
        }
    }

    private String extractPdfText(Path storagePath, Long documentId) {
        try (PDDocument document = Loader.loadPDF(storagePath.toFile())) {
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
