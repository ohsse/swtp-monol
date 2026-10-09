package com.mo.swtp.proc.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@link AiDrvnModeSseController} 통합 테스트 — 인증 우회 + Content-Type 검증.
 *
 * <p>SSE 는 응답 buffering + 스트림 유지로 HttpURLConnection.getResponseCode() 가
 * 첫 데이터 라인까지 block 될 수 있다. 본 테스트는 raw socket 으로 HTTP status line + 헤더만
 * 직접 읽어 검증한다.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AiDrvnModeSseControllerIntegrationTest {

    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    @LocalServerPort
    private int port;

    @Test
    void Authorization_헤더_없이_subscribe가_200을_반환하고_event_stream_Content_Type을_제공한다() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);

            // raw HTTP/1.1 GET (Authorization 헤더 없음)
            OutputStream out = socket.getOutputStream();
            String request = "GET /api/proc/PUMP_CONTROL/ai-mode/subscribe HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Accept: text/event-stream\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // status line + 헤더 라인 수집 (빈 라인 만나면 종료)
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            List<String> headers = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                headers.add(line);
            }

            assertThat(statusLine).startsWith("HTTP/1.1 200");
            assertThat(headers).anyMatch(h -> h.toLowerCase().startsWith("content-type:")
                    && h.toLowerCase().contains("text/event-stream"));
        }
    }
}
