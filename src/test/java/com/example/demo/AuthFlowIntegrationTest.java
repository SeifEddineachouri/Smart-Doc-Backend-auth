package com.example.demo;

import com.example.demo.model.entity.RoleEntity;
import com.example.demo.model.entity.UserEntity;
import com.example.demo.model.enums.LanguageCode;
import com.example.demo.model.enums.RoleName;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.service.AiDocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    @SuppressWarnings("unused")
    private AiDocumentIngestionService aiDocumentIngestionService;


    @Test
    void signupThenGetProfileWorks() throws Exception {
        String signupBody = """
            {
              "fullName": "Jane Doe",
              "workEmail": "jane@company.com",
              "password": "StrongPass#123",
              "acceptedTerms": true
            }
            """;

        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("jane@company.com"))
            .andExpect(jsonPath("$.user.isAdmin").value(false))
            .andReturn();

        String payload = signupResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(payload);
        String accessToken = root.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("jane@company.com"))
            .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    void uploadDocumentWorksOnH2WithoutDialectMismatch() throws Exception {
        String signupBody = """
            {
              "fullName": "Upload User",
              "workEmail": "upload@company.com",
              "password": "StrongPass#123",
              "acceptedTerms": true
            }
            """;

        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(signupResult.getResponse().getContentAsString());
        String accessToken = root.path("accessToken").asText();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "Hello SmartDoc upload".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(file)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("sample.txt"))
            .andExpect(jsonPath("$.mimeType").value(MediaType.TEXT_PLAIN_VALUE));
    }

    @Test
    void adminCanAccessAppWithoutPaymentPlan() throws Exception {
        RoleEntity adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
            .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN is missing in test data"));

        UserEntity admin = new UserEntity();
        admin.setFullName("Admin User");
        admin.setEmail("admin@company.com");
        admin.setPasswordHash(passwordEncoder.encode("StrongPass#123"));
        admin.setLanguage(LanguageCode.en);
        admin.setAcceptedTerms(true);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);

        String signinBody = """
            {
              "email": "admin@company.com",
              "password": "StrongPass#123",
              "rememberMe": false
            }
            """;

        MvcResult signinResult = mockMvc.perform(post("/api/v1/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signinBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.isAdmin").value(true))
            .andReturn();

        JsonNode root = objectMapper.readTree(signinResult.getResponse().getContentAsString());
        String accessToken = root.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("admin@company.com"))
            .andExpect(jsonPath("$.isAdmin").value(true));
    }
}


