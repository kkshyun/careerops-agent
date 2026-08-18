package com.careerops.backend.pkbimport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultipartUploadLimitIntegrationTest {
    @LocalServerPort int port;

    @Test
    void springMultipartConfigurationReturns413AboveTenMegabytes() throws Exception {
        String boundary = "careerops-boundary";
        byte[] prefix = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"documentType\"\r\n\r\nRESUME\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"large.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[prefix.length + 10 * 1024 * 1024 + 1 + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, body, prefix.length, 5);
        System.arraycopy(suffix, 0, body, body.length - suffix.length, suffix.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/career/imports/documents/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(413);
    }
}
