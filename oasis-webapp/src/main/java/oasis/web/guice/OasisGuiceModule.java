package oasis.web.guice;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;

import oasis.audit.AuditService;
import oasis.audit.NoopAuditService;
import oasis.audit.log4j.Log4JAuditService;
import oasis.audit.log4j.Log4JSupplier;
import oasis.model.directory.DirectoryRepository;
import oasis.services.directory.DummyDirectoryRepository;
import oasis.web.NettyOasisServer;
import oasis.web.OasisServer;
import oasis.web.Settings;
import oasis.web.SettingsLoader;

public class OasisGuiceModule extends AbstractModule {
  private static final Class<? extends AuditService> DEFAULT_AUDIT_SERVICE = NoopAuditService.class;
  private static final Logger logger = LoggerFactory.getLogger(OasisGuiceModule.class);

  private final Path configurationPath;

  public OasisGuiceModule(Path configurationPath) {
    this.configurationPath = configurationPath;
  }

  @Override
  protected void configure() {
    Settings settings = SettingsLoader.load(configurationPath);

    bind(Settings.class).toInstance(settings);

    bind(DirectoryRepository.class).to(DummyDirectoryRepository.class);
    bind(OasisServer.class).to(NettyOasisServer.class);

    Class<? extends AuditService> auditServiceImplClass = getAuditServiceImplClass(settings);
    if (Log4JAuditService.class.isAssignableFrom(auditServiceImplClass)) {
      Class<? extends Log4JSupplier> log4JSupplierImplClass = getLog4JSupplierImplClass(settings);
      if (log4JSupplierImplClass == null) {
        auditServiceImplClass = DEFAULT_AUDIT_SERVICE;
      } else {
        bind(Log4JSupplier.class).to(log4JSupplierImplClass);
      }
    }
    bind(AuditService.class).to(auditServiceImplClass);
  }

  private Class<? extends AuditService> getAuditServiceImplClass(Settings settings) {
    if (!settings.auditDisabled) {
      try {
        return getClassFromString(settings.auditService, AuditService.class);
      } catch (ClassNotFoundException e1) {
        logger.error("{} class does not exists or is unreachable. NoopAuditService used instead.", settings.auditService, e1);
      } catch (ClassCastException e2) {
        logger.error("{} class is not a subclass of AuditService. NoopAuditService used instead.", settings.auditService, e2);
      }
    }
    return DEFAULT_AUDIT_SERVICE;
  }

  private Class<? extends Log4JSupplier> getLog4JSupplierImplClass(Settings settings) {
    try {
      return getClassFromString(settings.auditLog4JSupplier, Log4JSupplier.class);
    } catch (ClassNotFoundException e1) {
      logger.error("{} class does not exists or is unreachable. NoopAuditService used instead of {}.",
          new Object[]{settings.auditLog4JSupplier, settings.auditService, e1});
    } catch (ClassCastException e2) {
      logger.error("{} class is not a subclass of Log4JSupplier. NoopAuditService used instead of {}.",
          new Object[]{settings.auditLog4JSupplier, settings.auditService, e2});
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private <T1 extends T2, T2> Class<T1> getClassFromString(String className, Class<T2> superClass) throws ClassNotFoundException {
    Class<T1> clazz = (Class<T1>) Class.forName(className);
    if (!superClass.isAssignableFrom(clazz)) {
      throw new ClassCastException();
    }
    return clazz;
  }
}
