package io.github.joseevb.result.spring.config;

import io.github.joseevb.result.Result;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/// Auto-configures transaction rollback when a transactional method returns [Result.Err].
@AutoConfiguration(
    afterName = "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration")
@ConditionalOnClass({Advisor.class, TransactionInterceptor.class})
@ConditionalOnBean(
    name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME,
    value = TransactionAttributeSource.class)
public class ResultTransactionAutoConfiguration {
  /// Creates the auto-configuration.
  public ResultTransactionAutoConfiguration() {}

  /// Adds rollback handling inside Spring's transaction interceptor.
  ///
  /// @param transactionAttributeSource Spring's source for transaction semantics
  /// @param transactionAdvisor Spring's transaction advisor
  /// @return the Result rollback advisor
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  @ConditionalOnMissingBean(name = "resultRollbackAdvisor")
  Advisor resultRollbackAdvisor(
      TransactionAttributeSource transactionAttributeSource,
      @Qualifier(TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
          BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor) {
    final StaticMethodMatcherPointcut pointcut =
        new StaticMethodMatcherPointcut() {
          @Override
          public boolean matches(Method method, Class<?> targetClass) {
            return Result.class.isAssignableFrom(method.getReturnType())
                && transactionAttributeSource.hasTransactionAttribute(method, targetClass);
          }
        };

    final MethodInterceptor interceptor =
        invocation -> {
          final Object result = invocation.proceed();
          if (result instanceof Result.Err<?, ?>) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
          }
          return result;
        };

    // The rollback interceptor must run inside Spring's TransactionInterceptor, so it needs lower
    // precedence than the transaction advisor. Spring commonly uses Ordered.LOWEST_PRECEDENCE for
    // that advisor and no lower-precedence value exists, so when Spring is already at
    // LOWEST_PRECEDENCE the transaction advisor is shifted inward by one and the Result advisor is
    // left at LOWEST_PRECEDENCE.
    final DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
    final int transactionOrder = transactionAdvisor.getOrder();
    if (transactionOrder == Ordered.LOWEST_PRECEDENCE) {
      transactionAdvisor.setOrder(Ordered.LOWEST_PRECEDENCE - 1);
      advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
    } else {
      advisor.setOrder(transactionOrder + 1);
    }
    return advisor;
  }
}
