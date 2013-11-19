package oasis.audit.noop;

import com.google.inject.AbstractModule;

import oasis.audit.AuditService;

public class NoopAuditModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(AuditService.class).to(NoopAuditService.class);
  }
}
