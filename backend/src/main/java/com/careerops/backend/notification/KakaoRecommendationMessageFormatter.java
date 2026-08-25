package com.careerops.backend.notification;

import org.springframework.stereotype.Component;

@Component
public class KakaoRecommendationMessageFormatter {
    public static final int MAX_LENGTH = 200;
    public String format(NotificationSendSnapshot snapshot) {
        String fixed = "[채용 추천]\n회사: " + value(snapshot.companyName())
                + "\n공고: " + value(snapshot.title())
                + "\n마감: " + value(snapshot.applicationEndAt())
                + "\n추천점수: " + snapshot.recommendationScore() + "\n이유: ";
        if (fixed.length() >= MAX_LENGTH) return fixed.substring(0, MAX_LENGTH);
        String reason = value(snapshot.reason());
        return fixed + reason.substring(0, Math.min(reason.length(), MAX_LENGTH - fixed.length()));
    }
    private String value(Object value) { return value == null ? "" : value.toString(); }
}
