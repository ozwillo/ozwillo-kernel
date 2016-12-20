/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class SettingsLoader {
  private static final Logger logger = LoggerFactory.getLogger(SettingsLoader.class);

  public static Config load(Path path) {
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
    return ConfigFactory.defaultOverrides()
        .withFallback(applicationConfig)
        .withFallback(ConfigFactory.defaultReference())
        .resolve();
  }
}
