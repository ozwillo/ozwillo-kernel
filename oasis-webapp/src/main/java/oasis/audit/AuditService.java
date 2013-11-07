package oasis.audit;

import com.google.common.base.Throwables;

public abstract class AuditService {
  /**
   * Log an event to the audit platform.
   *
   * @param logEvent The event which contains all data to send to the audit platform
   */
  protected abstract void log(LogEvent logEvent);

  /**
   * Create a LogEvent from an implementation class.
   */
  public <T extends LogEvent> T event(Class<T> clazz) {
    try {
      T logEvent = clazz.newInstance();
      logEvent.setAuditService(this);
      return logEvent;
    } catch (InstantiationException | IllegalAccessException e) {
      Throwables.propagate(e);
    }
    return null;
  }
}
