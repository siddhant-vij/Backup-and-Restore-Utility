package main.java.restore;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class RestoreManager {

  private final Configuration config;

  public RestoreManager(Configuration config) {
    this.config = config;
  }

  public void restore() throws IOException {
    Path backupZipPath = Path.of(config.getDefaultBackupDir(), "backup.zip");

    ZipFile zipFile = new ZipFile(backupZipPath.toFile());
    ConcurrentLinkedQueue<ZipEntry> allEntries = new ConcurrentLinkedQueue<>();
    ExecutorService readEntriesExecutor = Executors.newVirtualThreadPerTaskExecutor();

    zipFile.stream().forEach(entry -> {
      readEntriesExecutor.submit(() -> {
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
          byte[] bytes = new byte[inputStream.available()];
          inputStream.read(bytes);
          allEntries.add(entry);
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
      long estimatedTotalBytes = Files.size(backupZipPath);
      AtomicLong bytesRestored = new AtomicLong(0);
      System.out.println(
          "\nNo.of files to restore: " + totalFiles + " with disk space: " + estimatedTotalBytes / (1024 * 1024) + " MB");
      FileOperationsUtil.displayProgressRestore(bytesRestored, estimatedTotalBytes);

      try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupZipPath.toFile()))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          final ZipEntry finalEntry = entry;
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          byte[] buffer = new byte[8 * 1024];
          int bytesRead;
          while ((bytesRead = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
          }
          byte[] data = baos.toByteArray();

          Runnable restoreTask = () -> {
            try {
              Path destFile = restorePath.resolve(finalEntry.getName());
              Files.createDirectories(destFile.getParent());
              try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile.toFile()))) {
                bos.write(data);
                bytesRestored.addAndGet(data.length);
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          };
          restoreExecutor.submit(restoreTask);
        }
      }

      restoreExecutor.shutdown();
      try {
        restoreExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        System.out.println("\nRestore complete!");
      } catch (InterruptedException e) {
        System.out.println("\nRestore Interrupted!");
        e.printStackTrace();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
