package main.java.restore;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;
import main.java.util.KeyManagementUtil;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.crypto.SecretKey;

public class RestoreManager {

  private final Configuration config;
  private String encryptionPassword = null;
  private List<String> restoreIncludePatterns;
  private List<String> restoreExcludePatterns;
  private ConcurrentHashMap<String, String> storedFileHashes;
  private String hashFileDir;

  public RestoreManager(Configuration config) {
    this.config = config;
    this.restoreIncludePatterns = config.getRestoreIncludePatterns();
    this.restoreExcludePatterns = config.getRestoreExcludePatterns();
    this.hashFileDir = config.getHashFileDir();
  }

  public void restore() throws IOException {
    if (config.isEnableIntegrityCheckOnRestore()) {
      this.storedFileHashes = FileOperationsUtil.loadStoredFileHashes(hashFileDir);
    }
    Path backupZipPath = Path.of(config.getDefaultBackupDir(), "backup.zip");

    if (config.isEnableEncryption()) {
      System.out.print("\nEnter password for decryption: ");
      encryptionPassword = new String(System.console().readPassword());
    }

    SecretKey aesKeyFile = null;
    if (config.isEnableEncryption()) {
      try {
        aesKeyFile = (SecretKey) KeyManagementUtil.readKeyFromFile(config.getAesFileKeyDir() + "/aes.key", "AES",
            encryptionPassword);
      } catch (Exception e) {
        System.out.println("\nFailed to read AES key: " + e.getMessage() + "\n");
        System.exit(1);
      }
    }

    ZipFile zipFile = new ZipFile(backupZipPath.toFile());
    ConcurrentLinkedQueue<ZipEntry> allEntries = new ConcurrentLinkedQueue<>();
    ExecutorService readEntriesExecutor = Executors.newVirtualThreadPerTaskExecutor();
    AtomicLong estimatedTotalBytes = new AtomicLong(0);

    zipFile.stream().forEach(entry -> {
      readEntriesExecutor.submit(() -> {
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
          boolean include = FileOperationsUtil.matchPattern(entry.getName(), restoreIncludePatterns);
          boolean exclude = FileOperationsUtil.matchPattern(entry.getName(), restoreExcludePatterns);
          if (!include || exclude) {
            return;
          }

          byte[] bytes = new byte[inputStream.available()];
          inputStream.read(bytes);
          allEntries.add(entry);
          estimatedTotalBytes.addAndGet(entry.getSize());
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    });

    readEntriesExecutor.shutdown();
    try {
      readEntriesExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

      ExecutorService restoreExecutor = Executors.newVirtualThreadPerTaskExecutor();
      Path restorePath = Path.of(config.getDefaultRestoreDir());

      long totalFiles = allEntries.size();
      long totalBytes = estimatedTotalBytes.get();
      AtomicLong bytesRestored = new AtomicLong(0);
      System.out.println(
          "\nNo. of files to restore: " + totalFiles + " with disk space: " + totalBytes / (1024 * 1024)
              + " MB");
      Timer timer = FileOperationsUtil.displayProgressRestore(bytesRestored, totalBytes);
      AtomicBoolean shouldContinue = new AtomicBoolean(true);

      try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupZipPath.toFile()))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          boolean include = FileOperationsUtil.matchPattern(entry.getName(), restoreIncludePatterns);
          boolean exclude = FileOperationsUtil.matchPattern(entry.getName(), restoreExcludePatterns);
          if (!include || exclude) {
            continue;
          }

          final ZipEntry finalEntry = entry;
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          byte[] buffer = new byte[8 * 1024];
          int bytesRead;
          while ((bytesRead = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
          }
          byte[] data = baos.toByteArray();

          if (aesKeyFile != null) {
            data = KeyManagementUtil.decryptAES(data, aesKeyFile);
          }

          final byte[] finalData = data;
          if (config.isEnableIntegrityCheckOnRestore()) {
            String generatedHash = FileOperationsUtil.generateHash(finalData, config.getHashAlgorithm());
            String storedHash = storedFileHashes.get(finalEntry.getName());
            if (storedHash == null || !generatedHash.equals(storedHash)) {
              System.out.println("\n\nIntegrity check failed for file: " + finalEntry.getName());
              shouldContinue.set(false);
              timer.cancel();
              break;
            }
          }

          Runnable restoreTask = () -> {
            try {
              Path destFile = restorePath.resolve(finalEntry.getName());
              Files.createDirectories(destFile.getParent());
              try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile.toFile()))) {
                bos.write(finalData);
                bytesRestored.addAndGet(finalData.length);
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          };
          restoreExecutor.submit(restoreTask);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      restoreExecutor.shutdown();
      try {
        restoreExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        if (!shouldContinue.get()) {
          System.out.println("\nRestore operation terminated due to failed integrity check.\n");
          return;
        }
        System.out.println("\nRestore complete!");
        timer.cancel();
      } catch (InterruptedException e) {
        System.out.println("\nRestore Interrupted!");
        e.printStackTrace();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
