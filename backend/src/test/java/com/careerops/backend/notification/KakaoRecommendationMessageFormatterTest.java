package com.careerops.backend.notification;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class KakaoRecommendationMessageFormatterTest {
    private final KakaoRecommendationMessageFormatter formatter = new KakaoRecommendationMessageFormatter();
    @Test void usesOnlySnapshotValuesDeterministically() {
        var snapshot = snapshot("회사값", "제목값", "근거값", 0.87);
        String first = formatter.format(snapshot);
        assertThat(first).isEqualTo(formatter.format(snapshot)).contains("회사값", "제목값", "2026-09-30", "0.87", "근거값");
    }
    @Test void truncatesReasonAtExactTwoHundredCharacterBoundary() {
        String text = formatter.format(snapshot("회사", "제목", "가".repeat(300), 0.9));
        assertThat(text).hasSize(200);
        assertThat(text.substring(0, 199)).isEqualTo(formatter.format(snapshot("회사", "제목", "가".repeat(299), 0.9)).substring(0, 199));
    }
    private NotificationSendSnapshot snapshot(String company, String title, String reason, double score) {
        return new NotificationSendSnapshot(1L, 2L, company, title, LocalDate.of(2026, 9, 30),
                "https://example.invalid/job", score, reason, NotificationStatus.SENDING, null);
    }
}
