package main.java.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
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
        System.out.printf("\nProgress at time t + %d s: %.2f%% (%d MB / %d MB)",
            (int) (System.currentTimeMillis() / 1000 - startTime / 1000),
            displayedPercentage,
            totalBytesProcessed.get() / (1024 * 1024 * 2),
            totalBytesToProcess / (1024 * 1024 * 2));
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
        System.out.printf("\nProgress at time t + %d s: %.2f%% (%d MB / %d MB)",
            (int) (System.currentTimeMillis() / 1000 - startTime / 1000),
            percentage,
            totalBytesProcessed.get() / (1024 * 1024),
            totalBytesToProcess / (1024 * 1024));
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

  public static void createPartitionedBackup(List<Path> files, Path sourcePath, Path backupDir,
      boolean enableCompression, boolean enableEncryption, SecretKey aesKey, AtomicLong bytesBackedUp,
      AtomicLong totalBytesProcessed, boolean enableIntegrityCheck, String hashAlgorithm,
      ConcurrentHashMap<String, String> fileHashes) throws IOException {

    String tempFileName = "temp_" + UUID.randomUUID().toString() + ".zip";
    Path tempFile = backupDir.resolve(tempFileName);

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile.toFile()))) {
      if (enableCompression) {
        zos.setLevel(9);
      }

      for (Path file : files) {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Path effectivePath = sourcePath.relativize(file);
        ZipEntry zipEntry = new ZipEntry(effectivePath.toString());
        zos.putNextEntry(zipEntry);

        byte[] bytes = Files.readAllBytes(file);

        if (enableIntegrityCheck) {
          String hash = generateHash(bytes, hashAlgorithm);
          fileHashes.put(effectivePath.toString(), hash);
        }

        if (enableEncryption) {
          bytes = KeyManagementUtil.encryptAES(bytes, aesKey);
        }

        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
        bytesBackedUp.addAndGet(attrs.size());
        totalBytesProcessed.addAndGet(attrs.size());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void mergeTemporaryFilesIntoOne(Path outputFile, List<Path> tempFiles, boolean enableEncryption,
      SecretKey aesKey, AtomicLong totalBytesWritten, AtomicLong totalBytesProcessed, boolean enableIntegrityCheck,
      ConcurrentHashMap<String, String> fileHashes, String hashFileKeyDir) throws IOException {
    List<Path> filesToDelete = Collections.synchronizedList(new ArrayList<>());

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile.toFile()))) {
      ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

      for (Path tempFile : tempFiles) {
        executor.submit(() -> {
          try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
              byte[] buffer = new byte[8 * 1024];
              ByteArrayOutputStream baos = new ByteArrayOutputStream();

              int len;
              while ((len = zis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
              }

              byte[] fileData = baos.toByteArray();
              synchronized (zos) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                zos.write(fileData, 0, fileData.length);
                zos.closeEntry();
                totalBytesWritten.addAndGet(fileData.length);
              }
              zis.closeEntry();
            }
          } catch (IOException e) {
            e.printStackTrace();
          } catch (Exception e) {
            e.printStackTrace();
          }
          filesToDelete.add(tempFile);
        });
      }
      executor.shutdown();
      try {
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      if (enableIntegrityCheck) {
        try (BufferedOutputStream fos = new BufferedOutputStream(
            new FileOutputStream(hashFileKeyDir + "/hashes.json"))) {
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
    }

    for (Path tempFile : filesToDelete) {
      try {
        Files.deleteIfExists(tempFile.toAbsolutePath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
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
}
