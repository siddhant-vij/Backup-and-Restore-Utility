package main.java.util;

import java.io.ByteArrayOutputStream;
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

  public static void createPartitionedBackup(List<Path> files, Path sourcePath, Path backupDir,
      boolean enableCompression, boolean enableEncryption, SecretKey aesKey, AtomicLong bytesBackedUp,
      AtomicLong totalBytesProcessed) throws IOException {

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
      SecretKey aesKey, AtomicLong totalBytesWritten, AtomicLong totalBytesProcessed) throws IOException {
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
