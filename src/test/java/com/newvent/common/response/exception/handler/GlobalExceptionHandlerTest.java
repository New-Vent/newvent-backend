package com.newvent.common.response.exception.handler;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.newvent.common.exception.handler.GlobalExceptionHandler;

public class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 리소스를찾을수없으면_404를반환한다() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404-0"));
    }

    @Test
    void 지원하지않는메서드는_405와_Allow헤더를반환한다() throws Exception {
        mockMvc.perform(put("/test/events"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON405-0"))
                .andExpect(header().string(
                        HttpHeaders.ALLOW, containsString("GET")));
    }

    @Test
    void 잘못된상태값은_400을반환한다() throws Exception {
        mockMvc.perform(get("/test/events")
                        .param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"))
                .andExpect(jsonPath("$.message")
                        .value("'status' 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void 숫자가아닌경로변수는_400을반환한다() throws Exception {
        mockMvc.perform(get("/test/events/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"))
                .andExpect(jsonPath("$.message")
                        .value("'id' 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void 숫자가아닌페이지값은_400을반환한다() throws Exception {
        mockMvc.perform(get("/test/events")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"))
                .andExpect(jsonPath("$.message")
                        .value("'page' 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void 필수파라미터가없으면_400을반환한다() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-0"))
                .andExpect(jsonPath("$.message")
                        .value("'keyword' 파라미터는 필수입니다."));
    }

    @Test
    void 지원하지않는ContentType은_415를반환한다() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON415-0"));
    }

    @Test
    void 정상요청은_200을반환한다() throws Exception {
        mockMvc.perform(get("/test/events")
                        .param("status", "PUBLISHED")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.page").value(0));
    }

    enum TestStatus {
        DRAFT,
        PUBLISHED,
        ENDED
    }

    @RestController
    static class TestController {

        @GetMapping("/test/missing-resource")
        public void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(
                    HttpMethod.GET,
                    "/test/missing-resource",
                    "test/missing-resource"
            );
        }

        @GetMapping("/test/events")
        public Map<String, Object> events(
                @RequestParam(name = "status", defaultValue = "DRAFT")
                TestStatus status,
                @RequestParam(name = "page", defaultValue = "0")
                int page
        ) {
            return Map.of("status", status.name(), "page", page);
        }

        @GetMapping("/test/events/{id}")
        public Map<String, Long> event(
                @PathVariable("id") Long id
        ) {
            return Map.of("id", id);
        }

        @GetMapping("/test/required")
        public Map<String, String> required(
                @RequestParam("keyword") String keyword
        ) {
            return Map.of("keyword", keyword);
        }

        @PostMapping(
                value = "/test/body",
                consumes = MediaType.APPLICATION_JSON_VALUE
        )
        public Map<String, Object> body(
                @RequestBody Map<String, Object> body
        ) {
            return body;
        }
    }
}
