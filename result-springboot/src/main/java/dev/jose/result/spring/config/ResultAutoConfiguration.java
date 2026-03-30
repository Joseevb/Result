package dev.jose.result.spring.config;

import dev.jose.result.spring.ResultResponseAdvice;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ResultResponseAdvice.class)
public class ResultAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ResultResponseAdvice resultResponseAdvice(MessageSource messageSource, MeterRegistry meterRegistry) {
		return new ResultResponseAdvice(messageSource, meterRegistry);
	}
}
