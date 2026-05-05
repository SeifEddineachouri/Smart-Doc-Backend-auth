package com.example.demo;

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

    @MockBean
    @SuppressWarnings("unused")
    private AiDocumentIngestionService aiDocumentIngestionService;

    @Test
    void registerAliasWorksLikeSignup() throws Exception {
        String signupBody = """
            {
              "fullName": "Register User",
              "workEmail": "register@company.com",
              "password": "StrongPass#123",
              "acceptedTerms": true
            }
            """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("register@company.com"));
    }

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
            .andReturn();

        String payload = signupResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(payload);
        String accessToken = root.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("jane@company.com"));
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
}


