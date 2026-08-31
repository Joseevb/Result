package dev.jose.result.spring.config;

import dev.jose.result.spring.ResultResponseAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

/// Auto-configures Result response handling for Spring MVC applications.
@AutoConfiguration
@ConditionalOnClass(ResultResponseAdvice.class)
public class ResultAutoConfiguration {
  /// Creates the auto-configuration.
  public ResultAutoConfiguration() {}

  /// Creates the advice unless the application already provides one.
  ///
  /// @param messageSource the application message source
  /// @return the configured Result response advice
  @Bean
  @ConditionalOnMissingBean
  public ResultResponseAdvice resultResponseAdvice(MessageSource messageSource) {
    return new ResultResponseAdvice(messageSource);
  }
}
