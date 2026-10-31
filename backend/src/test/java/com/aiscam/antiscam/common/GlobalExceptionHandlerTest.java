package com.aiscam.antiscam.common;

import com.aiscam.antiscam.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class Failures {
        @GetMapping("/events") void disconnected(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.getWriter().write("data: ready\n\n");response.flushBuffer();
            throw new IOException("你的主机中的软件中止了一个已建立的连接。");
        }
        @GetMapping("/ordinary") void ordinary() {throw new IllegalStateException("test failure");}
    }
    @Test void committedSseFailureDoesNotAppendJsonToTheClosedStream() throws Exception {
        MockMvcBuilders.standaloneSetup(new Failures()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/events").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isOk())
                .andExpect(content().string("data: ready\n\n"));
    }
    @Test void uncommittedRequestStillGetsTheNormalInternalErrorResponse() throws Exception {
        MockMvcBuilders.standaloneSetup(new Failures()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/ordinary")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
