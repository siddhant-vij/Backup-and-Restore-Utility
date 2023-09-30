package main.java.config;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Configuration {

  private String defaultSourceDir;
  private String defaultBackupDir;
  private String defaultRestoreDir;
  private boolean enableCompression;

  private void readJsonConfig(String configFilePath) {
    JSONParser parser = new JSONParser();
    JSONObject configJson = null;
    try {
      if (configFilePath != null) {
        // Read the String input config.json
        configJson = (JSONObject) parser.parse(new FileReader(configFilePath.toString()));
      } else {
        // Read the default-config.json
        Path configClassPath = Path.of(Configuration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path configFile = Path.of(configClassPath.getParent().toString(), "config", "default-config.json");
        configJson = (JSONObject) parser.parse(new FileReader(configFile.toString()));
      }

      // Initialize fields with null checks
      if (configJson.get("defaultSourceDir") != null) {
        defaultSourceDir = (String) configJson.get("defaultSourceDir");
      }
      if (configJson.get("defaultBackupDir") != null) {
        defaultBackupDir = (String) configJson.get("defaultBackupDir");
      }
      if (configJson.get("defaultRestoreDir") != null) {
        defaultRestoreDir = (String) configJson.get("defaultRestoreDir");
      }
      if (configJson.get("enableCompression") != null) {
        enableCompression = (Boolean) configJson.get("enableCompression");
      }
    } catch (IOException | ParseException | URISyntaxException e) {
      System.out.println("Error reading configuration: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  public Configuration() {
    readJsonConfig(null);
  }

  public Configuration(String configFilePath) {
    readJsonConfig(configFilePath);
  }

  public String getDefaultSourceDir() {
    return defaultSourceDir;
  }

  public void setDefaultSourceDir(String defaultSourceDir) {
    this.defaultSourceDir = defaultSourceDir;
  }

  public String getDefaultBackupDir() {
    return defaultBackupDir;
  }

  public void setDefaultBackupDir(String defaultBackupDir) {
    this.defaultBackupDir = defaultBackupDir;
  }

  public String getDefaultRestoreDir() {
    return defaultRestoreDir;
  }

  public void setDefaultRestoreDir(String defaultRestoreDir) {
    this.defaultRestoreDir = defaultRestoreDir;
  }

  public boolean isEnableCompression() {
    return enableCompression;
  }

  public void setEnableCompression(boolean enableCompression) {
    this.enableCompression = enableCompression;
  }

  public void print() {
    Field[] fields = this.getClass().getDeclaredFields();
    System.out.println("\nCurrent Configuration:");

    int count = 0;
    for (Field field : fields) {
      field.setAccessible(true);
      try {
        System.out.println(++count + ". " + field.getName() + ": " + field.get(this));
      } catch (IllegalAccessException e) {
        System.out.println("Error reading field " + field.getName() + ": " + e.getMessage());
      }
    }
    System.out.println();
  }
}
