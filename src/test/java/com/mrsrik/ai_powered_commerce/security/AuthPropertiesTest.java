package com.mrsrik.ai_powered_commerce.security;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    private static String messages(Throwable t) {
        StringBuilder all = new StringBuilder();
        for (; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }

    @Test
    void absentPropertiesFailStartup() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(messages(ctx.getStartupFailure())).contains("app.auth");
        });
    }

    @Test
    void blankPropertyFailsStartup() {
        Stream.of(
                "app.auth.audience= ",
                "app.auth.client-id= ",
                "app.auth.client-secret= ",
                "app.auth.domain= ",
                "app.auth.connection= "
        ).forEach(blankProp ->
                runner.withPropertyValues(
                        "app.auth.audience=https://api.test.com",
                        "app.auth.client-id=test-id",
                        "app.auth.client-secret=test-secret",
                        "app.auth.domain=test.auth0.com",
                        "app.auth.connection=Username-Password-Authentication"
                ).withPropertyValues(blankProp).run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(messages(ctx.getStartupFailure())).contains("app.auth");
                }));
    }

    @Test
    void presentPropertiesSucceedAndToStringMasksSecret() {
        runner.withPropertyValues(
                "app.auth.audience=https://api.test.com",
                "app.auth.client-id=test-id",
                "app.auth.client-secret=super-secret-key",
                "app.auth.domain=test.auth0.com",
                "app.auth.connection=Username-Password-Authentication"
        ).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            AuthProperties props = ctx.getBean(AuthProperties.class);
            assertThat(props.audience()).isEqualTo("https://api.test.com");
            assertThat(props.clientId()).isEqualTo("test-id");
            assertThat(props.clientSecret()).isEqualTo("super-secret-key");
            assertThat(props.domain()).isEqualTo("test.auth0.com");
            assertThat(props.connection()).isEqualTo("Username-Password-Authentication");
            assertThat(props.toString()).doesNotContain("test-id");
            assertThat(props.toString()).contains("clientId=****");
            assertThat(props.toString()).doesNotContain("super-secret-key");
            assertThat(props.toString()).contains("clientSecret=****");
        });
    }
}
