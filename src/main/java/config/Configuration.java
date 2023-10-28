package main.java.config;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Configuration {

  private String defaultSourceDir;
  private String defaultBackupDir;
  private String defaultRestoreDir;
  private boolean enableCompression;
  private boolean enableEncryption;
  private String aesFileKeyDir;
  private List<String> backupIncludePatterns;
  private List<String> backupExcludePatterns;
  private List<String> restoreIncludePatterns;
  private List<String> restoreExcludePatterns;
  private boolean enableIntegrityCheck;
  private String hashAlgorithm;
  private String hashFileDir;
  private boolean enableLogging;
  private String logFileLocation;

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
      if (configJson.get("enableEncryption") != null) {
        enableEncryption = (Boolean) configJson.get("enableEncryption");
      }
      if (configJson.get("aesFileKeyDir") != null) {
        aesFileKeyDir = (String) configJson.get("aesFileKeyDir");
      }
      if (configJson.get("backupIncludePatterns") != null) {
        Object backupIncludeObj = configJson.get("backupIncludePatterns");
        if (backupIncludeObj instanceof List) {
          List<?> tempList = (List<?>) backupIncludeObj;
          if (tempList.stream().allMatch(item -> item instanceof String)) {
            backupIncludePatterns = tempList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
          }
        }
      }
      if (configJson.get("backupExcludePatterns") != null) {
        Object backupExcludeObj = configJson.get("backupExcludePatterns");
        if (backupExcludeObj instanceof List) {
          List<?> tempList = (List<?>) backupExcludeObj;
          if (tempList.stream().allMatch(item -> item instanceof String)) {
            backupExcludePatterns = tempList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
          }
        }
      }
      if (configJson.get("restoreIncludePatterns") != null) {
        Object restoreIncludeObj = configJson.get("restoreIncludePatterns");
        if (restoreIncludeObj instanceof List) {
          List<?> tempList = (List<?>) restoreIncludeObj;
          if (tempList.stream().allMatch(item -> item instanceof String)) {
            restoreIncludePatterns = tempList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
          }
        }
      }
      if (configJson.get("restoreExcludePatterns") != null) {
        Object restoreExcludeObj = configJson.get("restoreExcludePatterns");
        if (restoreExcludeObj instanceof List) {
          List<?> tempList = (List<?>) restoreExcludeObj;
          if (tempList.stream().allMatch(item -> item instanceof String)) {
            restoreExcludePatterns = tempList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
          }
        }
      }
      if (configJson.get("enableIntegrityCheck") != null) {
        enableIntegrityCheck = (Boolean) configJson.get("enableIntegrityCheck");
      }
      if (configJson.get("hashAlgorithm") != null) {
        hashAlgorithm = (String) configJson.get("hashAlgorithm");
      }
      if (configJson.get("hashFileDir") != null) {
        hashFileDir = (String) configJson.get("hashFileDir");
      }
      if (configJson.get("enableLogging") != null) {
        enableLogging = (Boolean) configJson.get("enableLogging");
      }
      if (configJson.get("logFileLocation") != null) {
        logFileLocation = (String) configJson.get("logFileLocation");
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

  public boolean isEnableEncryption() {
    return enableEncryption;
  }

  public String getAesFileKeyDir() {
    return aesFileKeyDir;
  }

  public List<String> getBackupIncludePatterns() {
    return backupIncludePatterns;
  }

  public List<String> getBackupExcludePatterns() {
    return backupExcludePatterns;
  }

  public List<String> getRestoreIncludePatterns() {
    return restoreIncludePatterns;
  }

  public List<String> getRestoreExcludePatterns() {
    return restoreExcludePatterns;
  }

  public boolean isEnableIntegrityCheck() {
    return enableIntegrityCheck;
  }

  public String getHashAlgorithm() {
    return hashAlgorithm;
  }

  public String getHashFileDir() {
    return hashFileDir;
  }

  public boolean isEnableLogging() {
    return enableLogging;
  }

  public String getLogFileLocation() {
    return logFileLocation;
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
