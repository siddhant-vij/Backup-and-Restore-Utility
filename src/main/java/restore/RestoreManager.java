package main.java.restore;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;
import main.java.util.KeyManagementUtil;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

import javax.crypto.SecretKey;

public class RestoreManager {

  private final Configuration config;
  private String encryptionPassword = null;
  private ConcurrentHashMap<String, String> storedFileHashes;
  private SecretKey aesKeyFile = null;
  private AtomicLong estimatedTotalBytes = new AtomicLong(0);
  private ConcurrentLinkedQueue<ZipEntry> allEntries = new ConcurrentLinkedQueue<>();

  public RestoreManager(Configuration config) {
    this.config = config;
  }

  public void restore() throws IOException {
    Path backupZipPath = Path.of(config.getDefaultBackupDir(), "backup.zip");
    ZipFile zipFile = new ZipFile(backupZipPath.toFile());
    initializeRestore();
    readZipEntries(zipFile);
    boolean isEnoughSpace = FileOperationsUtil.checkDiskSpace(estimatedTotalBytes.get(),
        Path.of(config.getDefaultRestoreDir()));
    if (!isEnoughSpace) {
      System.exit(1);
    }
    performRestore(zipFile);
  }

  private void initializeRestore() {
    if (config.isEnableIntegrityCheck()) {
      this.storedFileHashes = FileOperationsUtil.loadStoredFileHashes(config.getHashFileDir());
      System.out.print("\nEnter password for decryption: ");
      encryptionPassword = new String(System.console().readPassword());
      try {
        aesKeyFile = (SecretKey) KeyManagementUtil.readKeyFromFile(config.getAesFileKeyDir() + "/aes.key", "AES",
            encryptionPassword);
      } catch (Exception e) {
        System.out.println("\nFailed to read AES key: " + e.getMessage() + "\n");
        System.exit(1);
      }
    }
  }

  private void readSingleZipEntry(ZipFile zipFile, ZipEntry entry) {
    try (InputStream inputStream = zipFile.getInputStream(entry)) {
      boolean include = FileOperationsUtil.matchPattern(entry.getName(), config.getRestoreIncludePatterns());
      boolean exclude = FileOperationsUtil.matchPattern(entry.getName(), config.getRestoreExcludePatterns());
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
  }

  private void readZipEntries(ZipFile zipFile) throws IOException {
    ExecutorService readEntriesExecutor = Executors.newVirtualThreadPerTaskExecutor();
    zipFile.stream().forEach(entry -> readEntriesExecutor.submit(() -> readSingleZipEntry(zipFile, entry)));
    readEntriesExecutor.shutdown();
    try {
      readEntriesExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private byte[] readInputStream(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[8 * 1024];
    int bytesRead;
    while ((bytesRead = is.read(buffer)) != -1) {
      baos.write(buffer, 0, bytesRead);
    }
    return baos.toByteArray();
  }

  private void restoreEntry(ZipEntry entry, AtomicLong bytesRestored, AtomicBoolean shouldContinue,
      ExecutorService restoreExecutor, Path restorePath, ZipFile zipFile) {
    Runnable restoreTask = () -> {
      try {
        byte[] data = readInputStream(zipFile.getInputStream(entry));
        if (aesKeyFile != null) {
          data = KeyManagementUtil.decryptAES(data, aesKeyFile);
        }
        if (config.isEnableIntegrityCheck()) {
          String generatedHash = FileOperationsUtil.generateHash(data, config.getHashAlgorithm());
          String storedHash = storedFileHashes.get(entry.getName());
          if (storedHash == null || !generatedHash.equals(storedHash)) {
            System.out.println("\n\nIntegrity check failed for file: " + entry.getName());
            shouldContinue.set(false);
            return;
          }
        }
        Path destFile = restorePath.resolve(entry.getName());
        Files.createDirectories(destFile.getParent());
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile.toFile()))) {
          bos.write(data);
          bytesRestored.addAndGet(data.length);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
    restoreExecutor.submit(restoreTask);
  }

  private void performRestore(ZipFile zipFile) throws IOException {
    ExecutorService restoreExecutor = Executors.newVirtualThreadPerTaskExecutor();
    long totalFiles = allEntries.size();
    AtomicLong bytesRestored = new AtomicLong(0);
    AtomicBoolean shouldContinue = new AtomicBoolean(true);
    System.out.println("\nNo. of files to restore: " + totalFiles);
    Timer timer = FileOperationsUtil.displayProgressRestore(bytesRestored, estimatedTotalBytes.get());
    for (ZipEntry entry : allEntries) {
      restoreEntry(entry, bytesRestored, shouldContinue, restoreExecutor, Path.of(config.getDefaultRestoreDir()),
          zipFile);
    }
    finalizeRestore(restoreExecutor, shouldContinue, timer);
  }

  private void finalizeRestore(ExecutorService restoreExecutor, AtomicBoolean shouldContinue, Timer timer) {
    restoreExecutor.shutdown();
    try {
      restoreExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      if (!shouldContinue.get()) {
        System.out.println("\nRestore operation terminated due to failed integrity check.\n");
        timer.cancel();
        return;
      }
      System.out.println("\nRestore complete!");
      timer.cancel();
    } catch (InterruptedException e) {
      System.out.println("\nRestore Interrupted!");
      e.printStackTrace();
    }
  }
}
