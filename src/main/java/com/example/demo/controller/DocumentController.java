package com.example.demo.controller;

import com.example.demo.model.dto.UploadDocumentResponseDto;
import com.example.demo.model.dto.UploadedDocumentDto;
import com.example.demo.service.DocumentService;
import com.example.demo.util.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadDocumentResponseDto> upload(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("file") MultipartFile file,
        @RequestParam(name = "sessionId", required = false) UUID sessionId
    ) {
        return ResponseEntity.ok(documentService.upload(principal.getUser().getId(), file, sessionId));
    }

    @GetMapping
    public ResponseEntity<List<UploadedDocumentDto>> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(name = "sessionId", required = false) UUID sessionId
    ) {
        return ResponseEntity.ok(documentService.list(principal.getUser().getId(), sessionId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        documentService.delete(principal.getUser().getId(), id);
        return ResponseEntity.noContent().build();
    }
}



