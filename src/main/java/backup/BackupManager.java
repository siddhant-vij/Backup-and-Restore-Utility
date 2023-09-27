package main.java.backup;

import main.java.config.Configuration;
import main.java.util.Utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class BackupManager {

  private final Configuration config;

  public BackupManager(Configuration config) {
    this.config = config;
  }

  public void backup() throws IOException {
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    AtomicInteger filesBackedUp = new AtomicInteger(0);
    Path sourcePath = Path.of(config.getDefaultSourceDir());
    Path backupDir = Path.of(config.getDefaultBackupDir());

    // Count total number of files to backup
    long totalFiles = Files.walk(sourcePath).filter(Files::isRegularFile).count();

    // Check if parent path exists, otherwise assume current directory
    Path parentPath = (backupDir.getParent() != null) ? backupDir.getParent() : Paths.get(".");

    // Check if we have write permission
    if (!Files.isWritable(parentPath)) {
      throw new AccessDeniedException("Insufficient permissions to write to: " + backupDir.toString());
    }

    // Explicitly create the backup directory if it doesn't exist
    Files.createDirectories(backupDir);

    try {
      Files.walkFileTree(sourcePath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              try {
                Runnable backupTask = () -> {
                  try {
                    Path destFile = backupDir.resolve(sourcePath.relativize(file));
                    Utils.copyFile(file, destFile);
                    filesBackedUp.incrementAndGet();
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                };
                executorService.submit(backupTask);
              } catch (UncheckedIOException e) {
                e.printStackTrace();
              }
              return FileVisitResult.CONTINUE;
            }
          });

      // Progress display logic
      new Thread(() -> {
        while (filesBackedUp.get() < totalFiles) {
          double percentage = ((double) filesBackedUp.get() / totalFiles) * 100;
          System.out.println("Backup Progress: " + String.format("%.2f", percentage) + "%");
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }).start();

      executorService.shutdown();
      executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      System.out.println("Backup complete!");
    } catch (IOException e) {
      System.out.println("Backup failed: " + e.getMessage());
      throw e;
    } catch (InterruptedException e) {
      System.out.println("Backup interrupted: " + e.getMessage());
      executorService.shutdown();
      Thread.currentThread().interrupt();
    }
  }
}
