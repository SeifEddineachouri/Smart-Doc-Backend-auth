package com.example.demo.controller;

import com.example.demo.util.UserPrincipal;
import com.example.demo.model.dto.UpdateLanguageRequestDto;
import com.example.demo.model.dto.UserProfileResponseDto;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getCurrentUser(principal.getUser().getId()));
    }

    @PatchMapping("/me/language")
    public ResponseEntity<UserProfileResponseDto> updateLanguage(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateLanguageRequestDto request
    ) {
        return ResponseEntity.ok(userService.updateLanguage(principal.getUser().getId(), request));
    }
}



