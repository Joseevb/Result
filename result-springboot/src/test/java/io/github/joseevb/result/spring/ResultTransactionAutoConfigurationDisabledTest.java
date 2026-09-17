package io.github.joseevb.result.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.joseevb.result.Result;
import io.github.joseevb.result.spring.config.ResultTransactionAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(ResultTransactionAutoConfigurationTest.TestConfig.class)
@ImportAutoConfiguration(ResultTransactionAutoConfiguration.class)
@TestPropertySource(properties = "result.transactions.rollback-on-err=false")
class ResultTransactionAutoConfigurationDisabledTest {

  @Autowired private ApplicationContext applicationContext;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ResultTransactionAutoConfigurationTest.TransactionalService service;

  @BeforeEach
  void resetDatabase() {
    this.jdbc.execute("create table if not exists events (name varchar(100) not null)");
    this.jdbc.execute("delete from events");
  }

  @Test
  void disablesRollbackOnErr() {
    assertFalse(this.applicationContext.containsBean("resultRollbackAdvisor"));
    assertInstanceOf(Result.Err.class, this.service.err());
    assertEquals(1, this.eventCount("err"));
  }

  private int eventCount(String name) {
    final Integer count =
        this.jdbc.queryForObject("select count(*) from events where name = ?", Integer.class, name);
    return count == null ? 0 : count;
  }
}
