package io.github.joseevb.result.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.joseevb.result.Result;
import io.github.joseevb.result.spring.config.ResultTransactionAutoConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@SpringJUnitConfig(ResultTransactionAutoConfigurationTest.TestConfig.class)
@ImportAutoConfiguration(ResultTransactionAutoConfiguration.class)
class ResultTransactionAutoConfigurationTest {

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @Transactional
  @interface DomainTransaction {}

  static class TransactionalService {
    private final JdbcTemplate jdbc;
    private final NestedService nested;

    TransactionalService(JdbcTemplate jdbc, NestedService nested) {
      this.jdbc = jdbc;
      this.nested = nested;
    }

    @Transactional
    public Result<String, String> ok() {
      this.insert("ok");
      return Result.ok("ok");
    }

    @Transactional
    public Result<String, String> err() {
      this.insert("err");
      return Result.err("failed");
    }

    @Transactional
    public Result<String, String> recovered() {
      this.insert("recovered");
      return Result.<String, String>err("temporary").recover(error -> "recovered");
    }

    @Transactional
    public Result<String, DomainError> domainErr() {
      this.insert("domain-err");
      return Result.err(new DomainError("failed"));
    }

    @DomainTransaction
    public Result<String, String> composedErr() {
      this.insert("composed-err");
      return Result.err("failed");
    }

    @Transactional
    public Result<String, String> nestedRecovered() {
      final Result<String, String> nestedResult = this.nested.errRequiresNew();
      this.insert("outer");
      return nestedResult.recover(error -> "outer");
    }

    @Transactional
    public Result<String, String> nestedRequiredRecovered() {
      final Result<String, String> nestedResult = this.nested.errRequired();
      this.insert("outer-required");
      return nestedResult.recover(error -> "outer");
    }

    @Transactional
    public String plainTransactional() {
      this.insert("plain");
      return "plain";
    }

    @Transactional
    public Result<String, String> throwsException() {
      this.insert("exception");
      throw new IllegalStateException("failed");
    }

    public Result<String, String> nonTransactionalErr() {
      this.insert("non-transactional");
      return Result.err("failed");
    }

    private void insert(String value) {
      this.jdbc.update("insert into events (name) values (?)", value);
    }
  }

  @Transactional
  static class ClassTransactionalService {
    private final JdbcTemplate jdbc;

    ClassTransactionalService(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    public Result<String, String> err() {
      this.jdbc.update("insert into events (name) values ('class-err')");
      return Result.err("failed");
    }
  }

  static class NestedService {
    private final JdbcTemplate jdbc;

    NestedService(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Result<String, String> errRequired() {
      this.jdbc.update("insert into events (name) values ('inner-required')");
      return Result.err("failed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result<String, String> errRequiresNew() {
      this.jdbc.update("insert into events (name) values ('inner')");
      return Result.err("failed");
    }
  }

  interface TransactionalContract {
    @Transactional
    Result<String, String> err();
  }

  static class InterfaceTransactionalService implements TransactionalContract {
    private final JdbcTemplate jdbc;

    InterfaceTransactionalService(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public Result<String, String> err() {
      this.jdbc.update("insert into events (name) values ('interface-err')");
      return Result.err("failed");
    }
  }

  record DomainError(String message) {}

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @Import({
    TransactionalService.class,
    ClassTransactionalService.class,
    NestedService.class,
    InterfaceTransactionalService.class
  })
  static class TestConfig {
    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder()
          .generateUniqueName(true)
          .setType(EmbeddedDatabaseType.H2)
          .build();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionalService service;
  @Autowired private ClassTransactionalService classService;
  @Autowired private TransactionalContract interfaceService;

  @Autowired
  @Qualifier("resultRollbackAdvisor")
  private Advisor resultRollbackAdvisor;

  @BeforeEach
  void resetDatabase() {
    this.jdbc.execute("create table if not exists events (name varchar(100) not null)");
    this.jdbc.execute("delete from events");
  }

  @Test
  void commitsFinalOk() {
    assertInstanceOf(Result.Ok.class, this.service.ok());
    assertEquals(1, this.eventCount("ok"));
  }

  @Test
  void rollsBackFinalErr() {
    assertInstanceOf(Result.Err.class, this.service.err());
    assertEquals(0, this.eventCount("err"));
  }

  @Test
  void preservesNormalExceptionRollback() {
    assertThrows(IllegalStateException.class, this.service::throwsException);
    assertEquals(0, this.eventCount("exception"));
  }

  @Test
  void commitsErrRecoveredBeforeMethodReturns() {
    assertInstanceOf(Result.Ok.class, this.service.recovered());
    assertEquals(1, this.eventCount("recovered"));
  }

  @Test
  void rollsBackWithoutRequiringFailureErrorType() {
    assertInstanceOf(Result.Err.class, this.service.domainErr());
    assertEquals(0, this.eventCount("domain-err"));
  }

  @Test
  void supportsClassLevelTransactional() {
    assertInstanceOf(Result.Err.class, this.classService.err());
    assertEquals(0, this.eventCount("class-err"));
  }

  @Test
  void supportsComposedTransactionalAnnotation() {
    assertInstanceOf(Result.Err.class, this.service.composedErr());
    assertEquals(0, this.eventCount("composed-err"));
  }

  @Test
  void supportsTransactionalInterfaceDeclarations() {
    assertInstanceOf(Result.Err.class, this.interfaceService.err());
    assertEquals(0, this.eventCount("interface-err"));
  }

  @Test
  void honorsRequiresNewPropagation() {
    assertInstanceOf(Result.Ok.class, this.service.nestedRecovered());
    assertEquals(0, this.eventCount("inner"));
    assertEquals(1, this.eventCount("outer"));
  }

  @Test
  void sharesRequiredPropagationAndUnexpectedRollbackOnRecovery() {
    assertThrows(UnexpectedRollbackException.class, () -> this.service.nestedRequiredRecovered());
    assertEquals(0, this.eventCount("inner-required"));
    assertEquals(0, this.eventCount("outer-required"));
  }

  @Test
  void leavesNonTransactionalErrUnaffected() {
    assertInstanceOf(Result.Err.class, this.service.nonTransactionalErr());
    assertEquals(1, this.eventCount("non-transactional"));
  }

  @Test
  void ordersRollbackAdvisorInsideTransactionAdvisor() {
    this.service.plainTransactional();
    final Advisor[] advisors = assertInstanceOf(Advised.class, this.service).getAdvisors();
    final int transactionIndex = this.advisorIndex(advisors, TransactionInterceptor.class);
    final int resultIndex = this.advisorIndex(advisors, this.resultRollbackAdvisor);

    assertTrue(transactionIndex >= 0);
    assertTrue(resultIndex > transactionIndex);
  }

  private int eventCount(String name) {
    final Integer count =
        this.jdbc.queryForObject("select count(*) from events where name = ?", Integer.class, name);
    return count == null ? 0 : count;
  }

  private int advisorIndex(Advisor[] advisors, Class<?> adviceType) {
    for (int index = 0; index < advisors.length; index++) {
      if (adviceType.isInstance(advisors[index].getAdvice())) {
        return index;
      }
    }
    return -1;
  }

  private int advisorIndex(Advisor[] advisors, Advisor expected) {
    for (int index = 0; index < advisors.length; index++) {
      if (advisors[index] == expected) {
        return index;
      }
    }
    return -1;
  }
}
