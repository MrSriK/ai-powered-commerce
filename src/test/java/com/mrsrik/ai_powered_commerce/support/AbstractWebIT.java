package com.mrsrik.ai_powered_commerce.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for web integration tests that send real signed bearer tokens through the
 * full production security chain (decoder, validators, converter, authorization).
 */
@AutoConfigureMockMvc
@Import({ TestJwtDecoderConfig.class, TestCallers.class })
public abstract class AbstractWebIT extends AbstractPostgresIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestCallers callers;

    /** Performs the request with the caller's Authorization header. */
    protected ResultActions perform(MockHttpServletRequestBuilder request, TestCallers.Caller caller)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, caller.bearer()));
    }
}
