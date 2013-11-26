package oasis.auditlog.noop;

import com.google.inject.AbstractModule;

import oasis.auditlog.AuditLogService;

public class NoopAuditLogModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AuditLogService.class).to(NoopAuditLogService.class);
  }
}
