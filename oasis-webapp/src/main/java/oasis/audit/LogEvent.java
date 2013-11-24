package oasis.audit;

import org.joda.time.Instant;

import com.google.common.collect.ImmutableMap;

public abstract class LogEvent {
  private final String eventType;
  private final ImmutableMap.Builder<String, Object> contextMapBuilder;
  private final Instant date;
  private ImmutableMap<String, Object> contextMap;
  private AuditService auditService;

  public LogEvent(String eventType) {
    this(eventType, new Instant());
  }

  public LogEvent(String eventType, Instant date) {
    this.eventType = eventType;
    this.contextMapBuilder = ImmutableMap.builder();

    this.date = date;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getDate() {
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
