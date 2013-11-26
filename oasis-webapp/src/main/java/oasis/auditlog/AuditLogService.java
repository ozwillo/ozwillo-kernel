package oasis.auditlog;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.joda.time.Instant;

public abstract class AuditLogService {
  /**
   * Log an event to the audit log platform.
   *
   * @param auditLogEvent The event which contains all data to send to the audit log platform
   */
  protected abstract void log(AuditLogEvent auditLogEvent);

  /**
   * Create a AuditLogEvent from an implementation class.
   * <p>
   * The implementation class must define a constructor with no parameter.
   */
  public <T extends AuditLogEvent> T event(Class<T> clazz) {
    try {
      T logEvent = clazz.newInstance();
      logEvent.setAuditLogService(this);
      return logEvent;
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a AuditLogEvent from an implementation class and a date.
   * <p>
   * The implementation class must define a constructor with a date parameter.
   */
  public <T extends AuditLogEvent> T event(Class<T> clazz, Instant date) {
    try {
      Constructor<T> c = clazz.getConstructor(Instant.class);
      T logEvent = c.newInstance(date);
      logEvent.setAuditLogService(this);
      return logEvent;
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
