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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;

import com.typesafe.config.Config;

import de.thetaphi.forbiddenapis.SuppressForbidden;

public abstract class CommandLineTool {

  @Option(name = "-c", usage = "Configuration file", metaVar = "file")
  private Path configurationPath;

  @Option(name = "-l", usage = "Log4j configuration file", metaVar = "file")
  private Path log4jConfig;

  protected abstract Logger logger();

  protected Config init(String[] args) throws IOException {
    System.setProperty("org.jboss.logging.provider", "slf4j");

    parseArgs(args);

    if (log4jConfig != null && Files.isRegularFile(log4jConfig) && Files.isReadable(log4jConfig)) {
      System.setProperty("log4j.configurationFile", log4jConfig.toRealPath().toString());
    } else {
      // use default log4j configuration: all INFO and more to stdout
      System.setProperty("org.apache.logging.log4j.level", "INFO");

      if (log4jConfig != null) {
        if (!Files.isRegularFile(log4jConfig) || !Files.isReadable(log4jConfig)) {
          logger().warn("log4j2 configuration file not found or not readable. Using default configuration.");
        }
      } else {
        logger().debug("No log4j2 configuration file specified. Using default configuration.");
      }
    }

    return SettingsLoader.load(configurationPath);
  }

  private void parseArgs(String[] args) {
    CmdLineParser parser = new CmdLineParser(this);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      printUsage(e);
      System.exit(1);
    }
  }

  @SuppressForbidden
  protected void printUsage(CmdLineException e) {
    // TODO: detailed usage description
    System.err.println(e.getMessage());
    e.getParser().printUsage(System.err);
  }
}
