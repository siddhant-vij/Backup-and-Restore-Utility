package main.java.util;

import main.java.config.Configuration;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.SecretKey;

public class FileOperationsUtil {
  private static final double ADDITIONAL_SPACE_REQUIRED = 5.0;

  public static void checkAndCreateDir(Path dir) throws AccessDeniedException, IOException {
    Path parentPath = (dir.getParent() != null) ? dir.getParent() : dir;
    if (!Files.isWritable(parentPath)) {
      throw new AccessDeniedException("Insufficient permissions to write to: " + dir.toString());
    }
    Files.createDirectories(dir);
  }

  public static Timer displayProgressBackup(AtomicLong totalBytesProcessed, long totalBytesToProcess) {
    Timer timer = new Timer(true);
    final long startTime = System.currentTimeMillis();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        double rawPercentage = ((double) totalBytesProcessed.get() / totalBytesToProcess) * 100;
        if (rawPercentage >= 100.0) {
          timer.cancel();
        }
        double displayedPercentage = Math.min(100.0, rawPercentage);
        System.out.printf("\nProgress at time t + %d s: %.2f%%",
            (int) (System.currentTimeMillis() / 1000 - startTime / 1000),
            displayedPercentage);
      }
    }, 0, 5000);
    return timer;
  }

  public static Timer displayProgressRestore(AtomicLong totalBytesProcessed, long totalBytesToProcess) {
    Timer timer = new Timer(true);
    final long startTime = System.currentTimeMillis();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        double percentage = ((double) totalBytesProcessed.get() / totalBytesToProcess) * 100;
        if (percentage >= 100.0) {
          timer.cancel();
        }
        System.out.printf("\nProgress at time t + %d s: %.2f%%",
            (int) (System.currentTimeMillis() / 1000 - startTime / 1000),
            percentage);
      }
    }, 0, 5000);
    return timer;
  }

  public static void copyFile(Path src, Path dest) throws IOException {
    // Ensure parent directories exist
    if (dest.getParent() != null) {
      Files.createDirectories(dest.getParent());
    }
    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  public static boolean matchPattern(String filePath, List<String> patterns) {
    if (patterns.contains("all")) {
      return true;
    }
    if (patterns.contains("none")) {
      return false;
    }

    for (String pattern : patterns) {
      if (!pattern.contains("/") && !pattern.contains("\\")) {
        if (pattern.endsWith("."))
          pattern = "^.*\\\\" + pattern.split("\\.")[0] + "\\..*$";
        else if (pattern.startsWith("."))
          pattern = "^.*\\\\.*" + pattern + "$";
        else
          pattern = "^.*\\\\" + pattern + "$";
      }
      // This could be expanded to cover more use cases like case-sensitivity,
      // wildcards, folder-specificity, special characters, combinations, etc.

      if (Pattern.matches(pattern, filePath)) {
        return true;
      }
    }
    return false;
  }

  public static String generateHash(byte[] bytes, String algorithm) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance(algorithm);
    md.update(bytes);
    byte[] digest = md.digest();
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) {
      sb.append(String.format("%02x", b & 0xff));
    }
    return sb.toString();
  }

  private static Path generateTempFilePath(Path backupDir) {
    String tempFileName = "temp_" + UUID.randomUUID().toString() + ".zip";
    return backupDir.resolve(tempFileName);
  }

  private static ZipOutputStream initializeZipOutputStream(Path tempFile, boolean enableCompression)
      throws FileNotFoundException {
    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile.toFile()));
    if (enableCompression) {
      zos.setLevel(9); // Maximum compression level
    }
    return zos;
  }

  private static void processFileForBackup(Path file, ZipOutputStream zos, Path sourcePath, boolean enableEncryption,
      SecretKey aesKey, AtomicLong bytesBackedUp, AtomicLong totalBytesProcessed, boolean enableIntegrityCheck,
      String hashAlgorithm, ConcurrentHashMap<String, String> fileHashes) throws Exception {
    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
    ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(file).toString());
    zos.putNextEntry(zipEntry);

    byte[] bytes = Files.readAllBytes(file);

    if (enableIntegrityCheck) {
      String hash = generateHash(bytes, hashAlgorithm);
      fileHashes.put(zipEntry.getName(), hash);
    }

    if (enableEncryption) {
      bytes = KeyManagementUtil.encryptAES(bytes, aesKey);
    }

    zos.write(bytes);
    zos.closeEntry();
    bytesBackedUp.addAndGet(attrs.size());
    totalBytesProcessed.addAndGet(attrs.size());
  }

  public static void createPartitionedBackup(List<Path> files, Path sourcePath, Path backupDir, Configuration config,
      SecretKey aesKey, AtomicLong bytesBackedUp, AtomicLong totalBytesProcessed,
      ConcurrentHashMap<String, String> fileHashes) throws IOException {
    Path tempFile = generateTempFilePath(backupDir);
    try (ZipOutputStream zos = initializeZipOutputStream(tempFile, config.isEnableCompression())) {
      for (Path file : files) {
        processFileForBackup(file, zos, sourcePath, config.isEnableEncryption(), aesKey, bytesBackedUp,
            totalBytesProcessed, config.isEnableIntegrityCheck(), config.getHashAlgorithm(), fileHashes);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static byte[] readFromZipInputStream(ZipInputStream zis) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[8 * 1024];
    int len;
    while ((len = zis.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }
    return baos.toByteArray();
  }

  private static void processTempFile(Path tempFile, ZipOutputStream zos, AtomicLong totalBytesWritten) {
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempFile.toFile()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        byte[] fileData = readFromZipInputStream(zis);
        writeToZipOutputStream(entry, fileData, zos, totalBytesWritten);
        zis.closeEntry();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void writeToZipOutputStream(ZipEntry entry, byte[] fileData, ZipOutputStream zos,
      AtomicLong totalBytesWritten) throws IOException {
    synchronized (zos) {
      zos.putNextEntry(new ZipEntry(entry.getName()));
      zos.write(fileData, 0, fileData.length);
      zos.closeEntry();
      totalBytesWritten.addAndGet(fileData.length);
    }
  }

  private static void shutdownExecutor(ExecutorService executor) {
    executor.shutdown();
    try {
      executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void createJsonFromHashes(String hashFileKeyDir, ConcurrentHashMap<String, String> fileHashes)
      throws IOException {
    try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(hashFileKeyDir + "/hashes.json"))) {
      fos.write("{".getBytes());
      Iterator<Map.Entry<String, String>> iterator = fileHashes.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        String jsonEntry = "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"";
        fos.write(jsonEntry.getBytes());
        if (iterator.hasNext()) {
          fos.write(",".getBytes());
        }
      }
      fos.write("}".getBytes());
    }
  }

  private static void deleteTempFile(List<Path> tempFiles) {
    for (Path tempFile : tempFiles) {
      try {
        Files.deleteIfExists(tempFile.toAbsolutePath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void mergeTemporaryFilesIntoOne(Path outputFile, List<Path> tempFiles, AtomicLong totalBytesWritten,
      ConcurrentHashMap<String, String> fileHashes, Configuration config) throws IOException {
    List<Path> filesToDelete = Collections.synchronizedList(new ArrayList<>());
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile.toFile()))) {
      ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
      for (Path tempFile : tempFiles) {
        executor.submit(() -> {
          processTempFile(tempFile, zos, totalBytesWritten);
          filesToDelete.add(tempFile);
        });
      }
      shutdownExecutor(executor);
      if (config.isEnableIntegrityCheck()) {
        createJsonFromHashes(config.getHashFileDir(), fileHashes);
      }
    }
    deleteTempFile(tempFiles);
  }

  public static ConcurrentHashMap<String, String> loadStoredFileHashes(String hashFileDir) {
    try (BufferedReader reader = new BufferedReader(new FileReader(hashFileDir + "/hashes.json"))) {
      ConcurrentHashMap<String, String> storedFileHashes = new ConcurrentHashMap<>();
      StringBuilder key = new StringBuilder();
      StringBuilder value = new StringBuilder();
      boolean isKey = true;
      boolean isOpenQuote = false;

      int ch;
      while ((ch = reader.read()) != -1) {
        char c = (char) ch;
        if (c == '{')
          continue;
        if (c == '}')
          break;

        if (c == '\"') {
          isOpenQuote = !isOpenQuote;
          continue;
        }

        if (isOpenQuote) {
          if (isKey) {
            key.append(c);
          } else {
            value.append(c);
          }
        } else {
          if (c == ':') {
            isKey = false;
          }
          if (c == ',') {
            isKey = true;
            storedFileHashes.put(key.toString(), value.toString());
            key.setLength(0);
            value.setLength(0);
          }
        }
      }
      if (key.length() > 0 && value.length() > 0) {
        storedFileHashes.put(key.toString(), value.toString());
      }
      return storedFileHashes;
    } catch (Exception e) {
      System.out.println("Error reading stored hash values: " + e.getMessage());
      return new ConcurrentHashMap<>();
    }
  }

  public static boolean checkDiskSpace(long requiredSpace, Path dirPath) {
    File file = dirPath.toFile();
    long usableSpace = file.getUsableSpace();
    long additionalSpace = Math.round((ADDITIONAL_SPACE_REQUIRED / 100.0) * requiredSpace);
    long totalRequiredSpace = requiredSpace + additionalSpace;

    System.out.println("\nAvailable Disk Space: " + usableSpace / (1024 * 1024) + " MB");
    System.out.println("Required Disk Space: " + totalRequiredSpace / (1024 * 1024) + " MB");
    System.out
        .println("Add. Disk Space @ " + ADDITIONAL_SPACE_REQUIRED + "%: " + additionalSpace / (1024 * 1024) + " MB");

    if (usableSpace < totalRequiredSpace) {
      System.out.println("\nInsufficient disk space. Cannot proceed with the operation.");
      return false;
    } else {
      return true;
    }
  }
}
