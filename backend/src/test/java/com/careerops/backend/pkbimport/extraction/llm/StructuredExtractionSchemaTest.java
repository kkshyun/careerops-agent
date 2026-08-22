package com.careerops.backend.pkbimport.extraction.llm;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.careerops.backend.career.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredExtractionSchemaTest {
    @Test
    void sdkSchemaMakesEveryOptionalNonListFieldNullableButStillRequired() {
        Map<Class<?>, List<String>> optionalFields = Map.of(
                CareerExperienceCreateRequest.class,
                List.of("startDate", "endDate"),
                CertificationCreateRequest.class,
                List.of("acquiredDate", "expirationDate"),
                EducationCreateRequest.class,
                List.of("degree", "status", "startDate", "endDate", "gpa", "gpaScale"),
                AwardCreateRequest.class,
                List.of("awardedDate"),
                ExperienceBulletRequest.class,
                List.of("bulletType"));

        optionalFields.forEach((type, fields) -> {
            JsonNode schema = schemaFor(type);
            fields.forEach(field -> {
                assertThat(schema.path("required").toString()).contains("\"" + field + "\"");
                assertThat(declaresNullType(schema.path("properties").path(field)))
                        .as("%s.%s", type.getSimpleName(), field)
                        .isTrue();
            });
        });
    }

    @Test
    void sdkSchemaKeepsRequiredDomainFieldsNonNullable() {
        assertNonNullable(CareerExperienceCreateRequest.class,
                "type", "title", "organization", "role", "summary", "detail");
        assertNonNullable(CertificationCreateRequest.class,
                "name", "issuer", "credentialId", "description");
        assertNonNullable(EducationCreateRequest.class, "institution", "major", "description");
        assertNonNullable(AwardCreateRequest.class, "title", "issuer", "description");
        assertNonNullable(ExperienceBulletRequest.class, "content");
    }

    @Test
    void completeExtractionSchemaStaysWithinAnthropicUnionParameterLimit() {
        JsonNode schema = schemaFor(StructuredExtractionResult.class);

        int unionCount = countUnionSchemas(schema);

        assertThat(unionCount).isEqualTo(12).isLessThanOrEqualTo(16);
    }

    private void assertNonNullable(Class<?> type, String... fields) {
        JsonNode schema = schemaFor(type);
        for (String field : fields) {
            assertThat(schema.path("required").toString()).contains("\"" + field + "\"");
            assertThat(declaresNullType(schema.path("properties").path(field)))
                    .as("%s.%s", type.getSimpleName(), field)
                    .isFalse();
        }
    }

    private boolean declaresNullType(JsonNode fieldSchema) {
        return fieldSchema.findValues("type").stream().anyMatch(typeNode ->
                typeNode.isTextual() && "null".equals(typeNode.textValue())
                        || typeNode.isArray() && StreamSupport.stream(typeNode.spliterator(), false)
                        .anyMatch(value -> value.isTextual() && "null".equals(value.textValue())));
    }

    private int countUnionSchemas(JsonNode node) {
        int count = node.isObject()
                && (node.has("anyOf") || node.path("type").isArray()) ? 1 : 0;
        return count + StreamSupport.stream(node.spliterator(), false)
                .mapToInt(this::countUnionSchemas)
                .sum();
    }

    private <T> JsonNode schemaFor(Class<T> type) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model("claude-sonnet-5")
                .maxTokens(1)
                .addUserMessage("x")
                .outputConfig(type)
                .build();
        OutputConfig outputConfig = params.rawParams().outputConfig().orElseThrow();
        JsonOutputFormat format = outputConfig.format().orElseThrow();
        Map<String, JsonValue> schemaProperties = format.schema()._additionalProperties();

        JsonNode schema = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        schemaProperties.forEach((name, value) ->
                ((com.fasterxml.jackson.databind.node.ObjectNode) schema)
                        .set(name, value.convert(JsonNode.class)));
        return schema;
    }
}
