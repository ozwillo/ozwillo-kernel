package oasis.audit;

import java.util.Date;

import com.google.common.collect.ImmutableMap;

public abstract class LogEvent {
  private final String eventType;
  private final ImmutableMap.Builder<String, Object> contextMapBuilder;
  private final Date date;
  private ImmutableMap<String, Object> contextMap;
  private AuditService auditService;

  public LogEvent(String eventType) {
    this.eventType = eventType;
    this.contextMapBuilder = ImmutableMap.builder();

    this.date = new Date();
  }

  public String getEventType() {
    return eventType;
  }

  public Date getDate() {
    return date;
  }

  protected final void setAuditService(AuditService auditService) {
    this.auditService = auditService;
  }

  public ImmutableMap<String, Object> getContextMap() {
    if (contextMap == null) {
      contextMap = contextMapBuilder.build();
    }
    return contextMap;
  }

  protected void addContextData(String key, Object value) {
    contextMapBuilder.put(key, value);
  }

  protected boolean checkBeforeBuild() {
    return true;
  }

  /**
   * Send the event to the logger
   */
  public void log() {
    if (checkBeforeBuild()) {
      auditService.log(this);
    }
  }
}
