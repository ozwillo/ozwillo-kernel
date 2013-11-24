package oasis.audit;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.joda.time.Instant;

public abstract class AuditService {
  /**
   * Log an event to the audit platform.
   *
   * @param logEvent The event which contains all data to send to the audit platform
   */
  protected abstract void log(LogEvent logEvent);

  /**
   * Create a LogEvent from an implementation class.
   * <p>
   * The implementation class must define a constructor with no parameter.
   */
  public <T extends LogEvent> T event(Class<T> clazz) {
    try {
      T logEvent = clazz.newInstance();
      logEvent.setAuditService(this);
      return logEvent;
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a LogEvent from an implementation class and a date.
   * <p>
   * The implementation class must define a constructor with a date parameter.
   */
  public <T extends LogEvent> T event(Class<T> clazz, Instant date) {
    try {
      Constructor<T> c = clazz.getConstructor(Instant.class);
      T logEvent = c.newInstance(date);
      logEvent.setAuditService(this);
      return logEvent;
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
