package oasis.auditlog.log4j;

import com.google.inject.AbstractModule;

import oasis.auditlog.AuditLogService;

public class Log4JAuditLogModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(AuditLogService.class).to(Log4JAuditLogService.class);
  }
}
