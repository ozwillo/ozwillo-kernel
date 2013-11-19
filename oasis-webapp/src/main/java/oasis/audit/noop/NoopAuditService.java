package oasis.audit.noop;

import oasis.audit.AuditService;
import oasis.audit.LogEvent;

public class NoopAuditService extends AuditService {
  @Override
  public void log(LogEvent logEvent) {
    // Do nothing
  }
}
