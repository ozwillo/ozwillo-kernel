package oasis.audit;

public class NoopAuditService extends AuditService {
  @Override
  public void log(LogEvent logEvent) {
    // Do nothing
  }
}
