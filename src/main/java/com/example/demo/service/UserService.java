package com.example.demo.service;

import com.example.demo.model.enums.LanguageCode;
import com.example.demo.model.entity.UserEntity;
import com.example.demo.repository.UserRepository;
import com.example.demo.model.dto.UpdateLanguageRequestDto;
import com.example.demo.model.dto.UserProfileResponseDto;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDto getCurrentUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponseDto updateLanguage(UUID userId, UpdateLanguageRequestDto request) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));
        user.setLanguage(LanguageCode.valueOf(request.language()));
        UserEntity saved = userRepository.save(user);
        return toProfile(saved);
    }

    private UserProfileResponseDto toProfile(UserEntity user) {
        return new UserProfileResponseDto(
            user.getId().toString(),
            user.getFullName(),
            user.getEmail(),
            user.getLanguage().name()
        );
    }
}


