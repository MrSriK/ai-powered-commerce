package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;

class JwtDecoderWiringIT extends AbstractPostgresIT {

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void exactlyOneJwtDecoderBeanComesFromJwtDecoderConfig() {
        Map<String, JwtDecoder> decoders = context.getBeansOfType(JwtDecoder.class);
        assertThat(decoders).hasSize(1).containsKey("jwtDecoder");
        assertThat(context.getBeanFactory().getBeanDefinition("jwtDecoder"))
                .extracting(d -> d.getFactoryBeanName())
                .isEqualTo(context.getBeanNamesForType(JwtDecoderConfig.class)[0]);
    }
}
