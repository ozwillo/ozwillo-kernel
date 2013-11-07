package oasis.audit.log4j;

import javax.inject.Inject;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.async.AsyncLoggerConfig;
import org.apache.logging.log4j.core.config.BaseConfiguration;
import org.slf4j.LoggerFactory;

import oasis.audit.AuditService;
import oasis.audit.LogEvent;

public class Log4JAuditService extends AuditService {
  private static final String LOGGER_NAME = "OASIS_AUDIT_LOGGER";
  private static final String APPENDER_NAME = "OASIS_AUDIT_APPENDER";
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Log4JAuditService.class);

  private final Log4JSupplier log4JSupplier;
  private Logger auditLogger;

  @Inject
  protected Log4JAuditService(Log4JSupplier log4JSupplier) {
    super();

    this.log4JSupplier = log4JSupplier;
  }

  @Override
  protected void log(LogEvent logEvent) {
    synchronized (this) {
      if (auditLogger == null) {
        initLogger();
      }
    }

    auditLogger.info(this.log4JSupplier.generateMessage(logEvent));
  }

  private void initLogger() {
    auditLogger = (Logger) LogManager.getLogger(LOGGER_NAME);
    Appender appender = this.log4JSupplier.createAppender(APPENDER_NAME);

    if(appender == null) {
      logger.warn("Audit service cannot be used without Log4J Appender. Audit logger is now disabled.");
      auditLogger.setLevel(Level.OFF);
      return;
    }

    BaseConfiguration configuration = (BaseConfiguration) auditLogger.getContext().getConfiguration();

    AsyncLoggerConfig asyncLoggerConfig = new AsyncLoggerConfig(LOGGER_NAME, Level.ALL, false);
    asyncLoggerConfig.addAppender(appender, Level.ALL, null);
    asyncLoggerConfig.startFilter();
    if (!appender.isStarted()) {
      appender.start();
    }
    configuration.addLogger(LOGGER_NAME, asyncLoggerConfig);
    auditLogger.getContext().updateLoggers(configuration);
  }
}
