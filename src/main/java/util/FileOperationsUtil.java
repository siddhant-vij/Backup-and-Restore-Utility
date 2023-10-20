package main.java.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

  public static void displayProgress(AtomicLong bytesBackedUp, long totalBytes) {
    // Progress display logic
    new Thread(() -> {
      long timeCounter = 0L;
      System.out.println();
      while (bytesBackedUp.get() < totalBytes) {
        double percentage = ((double) bytesBackedUp.get() / totalBytes) * 100;
        System.out.println(
            "Progress at time t + " + timeCounter / 1000 + " s: " + String.format("%.2f", percentage) + "% (" +
                bytesBackedUp.get() / (1024 * 1024) + " MB / " + totalBytes / (1024 * 1024) + " MB)");

        try {
          Thread.sleep(5000);
          timeCounter += 5000;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }).start();
  }

  public static void copyFile(Path src, Path dest) throws IOException {
    // Ensure parent directories exist
    if (dest.getParent() != null) {
      Files.createDirectories(dest.getParent());
    }
    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  public static void compressFile(Path src, Path dest) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(dest.toFile());
        ZipOutputStream zos = new ZipOutputStream(fos)) {
      zos.setLevel(9); // set compression level: 0 (none) to 9 (maximum)
      ZipEntry zipEntry = new ZipEntry(src.getFileName().toString());
      zos.putNextEntry(zipEntry);
      Files.copy(src, zos);
      zos.closeEntry();
    }
  }

  public static void decompressFile(Path src, Path dest) throws IOException {
    try (FileInputStream fis = new FileInputStream(src.toFile());
        ZipInputStream zis = new ZipInputStream(fis)) {
      ZipEntry zipEntry = zis.getNextEntry();
      if (zipEntry != null) {
        Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
      }
      zis.closeEntry();
    }
  }
}
