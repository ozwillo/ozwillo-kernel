package oasis.auditlog;

import java.util.Map;

import org.joda.time.Instant;

public class RemoteAuditLogEvent extends AuditLogEvent {
  private static final String TYPE = "remote";

  public RemoteAuditLogEvent(Instant date) {
    super(TYPE, date);
  }

  public RemoteAuditLogEvent setLog(Map<String, Object> log) {
    // XXX: handle  mandatory fields ?

    for (Map.Entry<String, Object> entry : log.entrySet()) {
      this.addContextData(entry.getKey(), entry.getValue());
    }

    return this;
  }

}
