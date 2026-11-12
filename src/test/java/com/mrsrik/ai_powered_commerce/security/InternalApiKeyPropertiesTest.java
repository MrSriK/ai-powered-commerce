package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class InternalApiKeyPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InternalApiKeyProperties.class)
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
    void blankKeyFailsStartupNamingThePropertyOnly() {
        Stream.of("app.internal.api-key=", "app.internal.api-key=   ").forEach(prop ->
                runner.withPropertyValues(prop).run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(messages(ctx.getStartupFailure())).contains("app.internal.api-key");
                }));
    }

    @Test
    void absentKeyFailsStartup() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(messages(ctx.getStartupFailure())).contains("app.internal.api-key");
        });
    }

    @Test
    void presentKeyStartsAndToStringHidesIt() {
        runner.withPropertyValues("app.internal.api-key=s3cr3t-value").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            InternalApiKeyProperties props = ctx.getBean(InternalApiKeyProperties.class);
            assertThat(props.apiKey()).isEqualTo("s3cr3t-value");
            assertThat(props.toString()).doesNotContain("s3cr3t-value");
        });
    }
}
