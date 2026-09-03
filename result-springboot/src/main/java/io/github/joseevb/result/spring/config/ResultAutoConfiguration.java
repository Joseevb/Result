package io.github.joseevb.result.spring.config;

import io.github.joseevb.result.spring.ResultResponseAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/// Auto-configures Result response handling for Spring MVC applications.
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ResponseBodyAdvice.class)
public class ResultAutoConfiguration {
  /// Creates the auto-configuration.
  public ResultAutoConfiguration() {}

  /// Creates the advice unless the application already provides one.
  ///
  /// @param messageSource the application message source
  /// @return the configured Result response advice
  @Bean
  @ConditionalOnMissingBean
  ResultResponseAdvice resultResponseAdvice(MessageSource messageSource) {
    return new ResultResponseAdvice(messageSource);
  }
}
