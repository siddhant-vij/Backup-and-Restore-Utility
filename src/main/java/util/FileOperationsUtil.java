package main.java.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileOperationsUtil {
  public static void checkAndCreateDir(Path dir) throws AccessDeniedException, IOException {
    Path parentPath = (dir.getParent() != null) ? dir.getParent() : dir;
    if (!Files.isWritable(parentPath)) {
      throw new AccessDeniedException("Insufficient permissions to write to: " + dir.toString());
    }
    Files.createDirectories(dir);
  }

  public static void displayProgressBackup(AtomicLong totalBytesProcessed, long totalBytesToProcess) {
    Timer timer = new Timer();
    final long startTime = System.currentTimeMillis();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        double rawPercentage = ((double) totalBytesProcessed.get() / totalBytesToProcess) * 100;
        double displayedPercentage = Math.min(100.0, rawPercentage);
        System.out.printf("\nProgress at time t + %d s: %.2f%% (%d MB / %d MB)",
            (int) (System.currentTimeMillis() / 1000 - startTime / 1000),
            displayedPercentage,
            totalBytesProcessed.get() / (1024 * 1024 * 2),
            totalBytesToProcess / (1024 * 1024 * 2));
        if (rawPercentage >= 100.0) {
          timer.cancel();
        }
      }
    }, 0, 5000);
  }

  public static void displayProgressRestore(AtomicLong totalBytesProcessed, long totalBytesToProcess) {
    Timer timer = new Timer();
    final long startTime = System.currentTimeMillis();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        double rawPercentage = ((double) totalBytesProcessed.get() / totalBytesToProcess) * 100;
        double displayedPercentage = Math.min(100.0, rawPercentage);
        System.out.printf("\nProgress at time t + %d s: %.2f%% (%d MB / %d MB)",
            (int) (System.currentTimeMillis() / 1000 - startTime / 1000),
            displayedPercentage,
            totalBytesProcessed.get() / (1024 * 1024),
            totalBytesToProcess / (1024 * 1024));
        if (rawPercentage >= 100.0) {
          timer.cancel();
        }
      }
    }, 0, 5000);
  }

  public static void copyFile(Path src, Path dest) throws IOException {
    // Ensure parent directories exist
    if (dest.getParent() != null) {
      Files.createDirectories(dest.getParent());
    }
    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  public static void createPartitionedBackup(List<Path> files, Path sourcePath, Path backupDir,
      boolean enableCompression, AtomicLong bytesBackedUp, AtomicLong totalBytesProcessed) throws IOException {

    String tempFileName = "temp_" + UUID.randomUUID().toString() + ".zip";
    Path tempFile = backupDir.resolve(tempFileName);

    if (enableCompression) {
      ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile.toFile()));
      zos.setLevel(9);
      for (Path file : files) {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Path effectivePath = sourcePath.relativize(file);
        ZipEntry zipEntry = new ZipEntry(effectivePath.toString());
        zos.putNextEntry(zipEntry);
        byte[] bytes = Files.readAllBytes(file);
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
        bytesBackedUp.addAndGet(attrs.size());
        totalBytesProcessed.addAndGet(attrs.size());
      }
      zos.close();
    } else {
      for (Path file : files) {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Path effectivePath = sourcePath.relativize(file);
        Path destFile = backupDir.resolve(effectivePath);
        Files.createDirectories(destFile.getParent());
        Files.copy(file, destFile);
        bytesBackedUp.addAndGet(attrs.size());
        totalBytesProcessed.addAndGet(attrs.size());
      }
    }
  }

  public static void mergeTemporaryFilesIntoOne(Path outputFile, List<Path> tempFiles, AtomicLong totalBytesWritten,
      AtomicLong totalBytesProcessed) throws IOException {
    List<Path> filesToDelete = Collections.synchronizedList(new ArrayList<>());

    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile.toFile()))) {
      ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

      for (Path tempFile : tempFiles) {
        executor.submit(() -> {
          try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
              synchronized (zos) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                byte[] buffer = new byte[8 * 1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                  zos.write(buffer, 0, len);
                  totalBytesWritten.addAndGet(len);
                }
                zos.closeEntry();
              }
              zis.closeEntry();
            }
          } catch (IOException e) {
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
    }

    for (Path tempFile : filesToDelete) {
      try {
        Files.deleteIfExists(tempFile.toAbsolutePath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
