package oasis.auditlog.noop;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;

public class NoopAuditLogService extends AuditLogService {
  @Override
  public void log(AuditLogEvent auditLogEvent) {
    // Do nothing
  }
}
