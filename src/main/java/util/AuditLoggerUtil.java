/*
 * Use auditLogger.logActivity() to get logging to work
 * Tested & Working on Windows 11 (64-bit) - JDK 21
 */

package main.java.util;

import main.java.config.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class AuditLoggerUtil {
  private final ReentrantLock lock = new ReentrantLock();
  private boolean isLoggingEnabled;
  private final String LOG_FILE_PATH;
  private final DateTimeFormatter formatter;

  // Change these values as needed
  private static final long NINETY_DAYS_IN_MILLIS = 7776000000L; // 90 days in milliseconds
  private static final long SIX_MONTHS_IN_MILLIS = 15552000000L; // 6 months in milliseconds

  public enum LogLevel {
    ERROR, WARN, INFO, DEBUG
  }

  public AuditLoggerUtil(Configuration config) {
    isLoggingEnabled = config.isEnableLogging();
    LOG_FILE_PATH = config.getLogFileLocation();
    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    if (isLoggingEnabled)
      init();
  }

  public void init() {
    try {
      long currentTime = System.currentTimeMillis();
      Path logPath = Paths.get(LOG_FILE_PATH);
      Path oldLogPath = Paths.get(LOG_FILE_PATH + ".old");
      if (Files.exists(logPath)) {
        BasicFileAttributes attrs = Files.readAttributes(logPath, BasicFileAttributes.class);
        long creationTime = attrs.creationTime().toMillis();
        if ((currentTime - creationTime) > NINETY_DAYS_IN_MILLIS) {
          byte[] logData = Files.readAllBytes(logPath);
          Files.write(oldLogPath, logData, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
          Files.newBufferedWriter(logPath, StandardOpenOption.TRUNCATE_EXISTING).close();
        }
      } else {
        Files.createFile(logPath);
      }
      if (Files.exists(oldLogPath)) {
        BasicFileAttributes oldAttrs = Files.readAttributes(oldLogPath, BasicFileAttributes.class);
        long oldCreationTime = oldAttrs.creationTime().toMillis();
        if ((currentTime - oldCreationTime) > SIX_MONTHS_IN_MILLIS) {
          Files.delete(oldLogPath);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Error initializing audit log", e);
    }
  }

  @SuppressWarnings("unchecked")
  public void logActivity(String activityType, String status, String details, LogLevel level) {
    if (!isLoggingEnabled) {
      return;
    }
    lock.lock();
    try {
      String timestamp = LocalDateTime.now().format(formatter);
      JSONObject logEntryJson = new JSONObject();
      logEntryJson.put("timestamp", timestamp);
      logEntryJson.put("activityType", activityType);
      logEntryJson.put("status", status);
      logEntryJson.put("details", details);
      logEntryJson.put("level", level.toString());
      try (FileWriter writer = new FileWriter(LOG_FILE_PATH, true)) {
        writer.write(logEntryJson.toString() + "\n");
      }
    } catch (IOException e) {
      throw new RuntimeException("Error writing to audit log", e);
    } finally {
      lock.unlock();
    }
  }

  public List<String> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) throws IOException {
    List<String> allLogs = Files.readAllLines(Paths.get(LOG_FILE_PATH));
    JSONParser parser = new JSONParser();
    return allLogs.stream()
        .filter(log -> {
          JSONObject logJson;
          try {
            logJson = (JSONObject) parser.parse(log);
          } catch (ParseException e) {
            e.printStackTrace();
            return false;
          }
          LocalDateTime logDate = LocalDateTime.parse((String) logJson.get("timestamp"), formatter);
          return !logDate.isBefore(startDate) && !logDate.isAfter(endDate);
        })
        .collect(Collectors.toList());
  }

  public List<String> getLogsByActivityType(String activityType) throws IOException {
    List<String> allLogs = Files.readAllLines(Paths.get(LOG_FILE_PATH));
    JSONParser parser = new JSONParser();
    return allLogs.stream()
        .filter(log -> {
          JSONObject logJson;
          try {
            logJson = (JSONObject) parser.parse(log);
          } catch (ParseException e) {
            e.printStackTrace();
            return false;
          }
          return activityType.equals((String) logJson.get("activityType"));
        })
        .collect(Collectors.toList());
  }

  public List<String> getLogsByStatusType(String statusType) throws IOException {
    List<String> allLogs = Files.readAllLines(Paths.get(LOG_FILE_PATH));
    JSONParser parser = new JSONParser();
    return allLogs.stream()
        .filter(log -> {
          JSONObject logJson;
          try {
            logJson = (JSONObject) parser.parse(log);
          } catch (ParseException e) {
            e.printStackTrace();
            return false;
          }
          return statusType.equals((String) logJson.get("status"));
        })
        .collect(Collectors.toList());
  }
}
