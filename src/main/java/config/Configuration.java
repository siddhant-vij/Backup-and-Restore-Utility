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

  public Configuration() {
    try {
      // Read the default-config.json
      Path configClassPath = Path.of(Configuration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      Path configFilePath = Path.of(configClassPath.getParent().toString(), "config", "default-config.json");
      JSONParser parser = new JSONParser();
      JSONObject configJson = (JSONObject) parser.parse(new FileReader(configFilePath.toString()));

      // Initialize fields
      defaultSourceDir = (String) configJson.get("defaultSourceDir");
      defaultBackupDir = (String) configJson.get("defaultBackupDir");
      defaultRestoreDir = (String) configJson.get("defaultRestoreDir");

    } catch (IOException | ParseException | URISyntaxException e) {
      System.out.println("Error reading configuration: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  public Configuration(String configFilePath) {
    try {
      // Read the String input config.json
      JSONParser parser = new JSONParser();
      JSONObject configJson = (JSONObject) parser.parse(new FileReader(configFilePath.toString()));

      // Initialize fields
      defaultSourceDir = (String) configJson.get("defaultSourceDir");
      defaultBackupDir = (String) configJson.get("defaultBackupDir");
      defaultRestoreDir = (String) configJson.get("defaultRestoreDir");

    } catch (IOException | ParseException e) {
      System.out.println("Error reading configuration: " + e.getMessage());
      throw new RuntimeException(e);
    }
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

  public void print() {
    Field[] fields = this.getClass().getDeclaredFields();
    System.out.println("Current Configuration:");

    for (Field field : fields) {
      field.setAccessible(true);
      try {
        System.out.println(field.getName() + ": " + field.get(this));
      } catch (IllegalAccessException e) {
        System.out.println("Error reading field " + field.getName() + ": " + e.getMessage());
      }
    }
  }
}
