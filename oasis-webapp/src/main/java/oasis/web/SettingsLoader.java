package oasis.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import oasis.web.Settings.Builder;

public class SettingsLoader {
  private static final Logger logger = LoggerFactory.getLogger(SettingsLoader.class);

  public static Settings load(Path path) {
    Config applicationConfig = ConfigFactory.empty();
    if (path != null) {
      if (Files.isRegularFile(path) && Files.isReadable(path)) {
        applicationConfig = ConfigFactory.parseFileAnySyntax(path.toFile())
            .withFallback(ConfigFactory.parseMap(Collections.singletonMap("oasis.conf-dir", path.getParent().toAbsolutePath().toString())));
      } else {
        logger.warn("Configuration file not found or not readable. Using default configuration.");
      }
    } else {
      logger.debug("No configuration file specified. Using default configuration.");
    }
    // TODO: handle fallback manually, to fallback with a warning in case of illegal values, instead of throwing
    Config config = ConfigFactory.defaultOverrides()
        .withFallback(applicationConfig)
        .withFallback(ConfigFactory.defaultReference())
        .resolve();

    return fromConfig(config);
  }

  public static Settings fromConfig(Config config) {
    return fromConfig(Settings.builder(), config).build();
  }

  public static Builder fromConfig(Builder builder, Config config) {
    Path confDir = getDir(config, null, "oasis.conf-dir");
    builder.setNettyPort(config.getInt("oasis.netty.port"));

    // TODO
    return builder;
  }

  private static Path getPath(Config config, Path parent, String path) {
    String filename = config.hasPath(path) ? config.getString(path) : null;
    if (filename == null || filename.isEmpty()) {
      return null;
    }
    if (parent == null) {
      return Paths.get(filename);
    }
    return parent.resolve(filename);
  }

  /**
   * Similar to {@link #getPath(Config, Path, String)} but also tries to the directory if it doesn't exist.
   */
  private static Path getDir(Config config, Path parent, String path) {
    Path dir = getPath(config, parent, path);
    if (dir != null) {
      if (!ensureDir(dir)) {
        dir = null;
      }
    }
    return dir;
  }

  /**
   * Returns whether the file exists and is writable, or can be created.
   * <p>
   * Note: the parent directory will tentatively be created if it doesn't exist,
   * which can create some of the parent directories.
   *
   * @see java.nio.file.Files#createDirectories(java.nio.file.Path, java.nio.file.attribute.FileAttribute[])
   */
  private static boolean canWrite(Path path) {
    if (Files.isRegularFile(path)) {
      return Files.isWritable(path);
    }

    Path parent = path.getParent();
    return ensureDir(parent) && Files.isWritable(parent);
  }

  private static boolean ensureDir(Path dir) {
    if (Files.exists(dir)) {
      return Files.isDirectory(dir);
    }

    try {
      Files.createDirectories(dir);
      assert Files.isDirectory(dir);
      return true;
    } catch (IOException e) {
      logger.warn("The directory path {} cannot be created.", dir, e);
    }

    return false;
  }
}
