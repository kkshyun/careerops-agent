package com.careerops.backend.pkbimport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class SourceDocumentControllerTest {
    private static final String DOCUMENTS_URL = "/api/career/imports/documents";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SourceDocumentRepository repository;

    @Test
    void createsMinimalAndFullDocuments() throws Exception {
        mockMvc.perform(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"RESUME\",\"rawText\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").isEmpty())
                .andExpect(jsonPath("$.documentType").value("RESUME"))
                .andExpect(jsonPath("$.rawText").value("hello"))
                .andExpect(jsonPath("$.contentHash").value(
                        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"));

        mockMvc.perform(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON).content("""
                {"fileName":"portfolio.txt","documentType":"PORTFOLIO","rawText":"프로젝트 원문"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("portfolio.txt"))
                .andExpect(jsonPath("$.documentType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.rawText").value("프로젝트 원문"))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")));
    }

    @Test
    void rejectsInvalidRequestsWithoutCreatingRows() throws Exception {
        long before = repository.count();
        String[] requests = {
                "{\"rawText\":\"text\"}",
                "{\"documentType\":\"RESUME\"}",
                "{\"documentType\":\"RESUME\",\"rawText\":\"   \"}",
                objectMapper.writeValueAsString(new RequestBody(null, "RESUME", "x".repeat(50_001)))
        };
        for (String request : requests) {
            mockMvc.perform(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void acceptsFiftyThousandCharacterBoundary() throws Exception {
        String rawText = "x".repeat(50_000);
        JsonNode response = responseJson(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequestBody(null, "OTHER", rawText))));

        assertThat(response.get("rawText").asText()).hasSize(50_000);
    }

    @Test
    void permitsDuplicateRawTextWithSameHashAndDifferentIds() throws Exception {
        String body = "{\"documentType\":\"EXPERIENCE_NOTE\",\"rawText\":\"duplicate source\"}";
        JsonNode first = responseJson(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON).content(body));
        JsonNode second = responseJson(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON).content(body));

        assertThat(first.get("id").asLong()).isNotEqualTo(second.get("id").asLong());
        assertThat(first.get("contentHash").asText()).isEqualTo(second.get("contentHash").asText());
    }

    @Test
    void listsWithPaginationClampAndFilterWithoutRawText() throws Exception {
        repository.save(new SourceDocument("resume-1", DocumentType.RESUME, "a".repeat(64), "secret one"));
        repository.save(new SourceDocument("resume-2", DocumentType.RESUME, "b".repeat(64), "secret two"));
        repository.saveAndFlush(new SourceDocument("other", DocumentType.OTHER, "c".repeat(64), "secret three"));

        mockMvc.perform(get(DOCUMENTS_URL).param("documentType", "RESUME").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].documentType").value("RESUME"))
                .andExpect(jsonPath("$.content[0].rawText").doesNotExist());
        mockMvc.perform(get(DOCUMENTS_URL).param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void getsExistingDocumentWithRawTextAndReturnsNotFoundForMissingOne() throws Exception {
        SourceDocument document = repository.saveAndFlush(
                new SourceDocument("resume", DocumentType.RESUME, "d".repeat(64), "detail text"));
        mockMvc.perform(get(DOCUMENTS_URL + "/{id}", document.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rawText").value("detail text"));
        mockMvc.perform(get(DOCUMENTS_URL + "/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void rawTextIsNotWrittenToApplicationLogsDuringCreateAndRead() throws Exception {
        String sensitiveRawText = "RAW_TEXT_MUST_NOT_APPEAR_7c09e67b";
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        try {
            JsonNode created = responseJson(post(DOCUMENTS_URL).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new RequestBody("private.txt", "RESUME", sensitiveRawText))));
            mockMvc.perform(get(DOCUMENTS_URL + "/{id}", created.get("id").asLong())).andExpect(status().isOk());

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitiveRawText));
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private JsonNode responseJson(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String response = mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private record RequestBody(String fileName, String documentType, String rawText) {}
}
