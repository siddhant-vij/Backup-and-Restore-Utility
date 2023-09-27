package main.java.restore;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RestoreManager {

  private final Configuration config;

  public RestoreManager(Configuration config) {
    this.config = config;
  }

  public void restore() throws IOException {
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    AtomicInteger filesRestored = new AtomicInteger(0);
    Path backupPath = Path.of(config.getDefaultBackupDir());
    Path restorePath = Path.of(config.getDefaultRestoreDir());

    // Count total number of files to restore
    long totalFiles = Files.walk(backupPath).filter(Files::isRegularFile).count();

    // Check if parent path exists, otherwise assume current directory
    Path parentPath = (restorePath.getParent() != null) ? restorePath.getParent() : Paths.get(".");

    // Check if we have write permission
    if (!Files.isWritable(parentPath)) {
      throw new AccessDeniedException("Insufficient permissions to write to: " + restorePath.toString());
    }

    // Explicitly create the restore directory if it doesn't exist
    Files.createDirectories(restorePath);

    try {
      Files.walkFileTree(backupPath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              try {
                Runnable restoreTask = () -> {
                  try {
                    Path destFile = restorePath.resolve(backupPath.relativize(file));
                    Utils.copyFile(file, destFile);
                    filesRestored.incrementAndGet();
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                };
                executorService.submit(restoreTask);
              } catch (UncheckedIOException e) {
                e.printStackTrace();
              }
              return FileVisitResult.CONTINUE;
            }
          });

      // Progress display logic
      new Thread(() -> {
        while (filesRestored.get() < totalFiles) {
          double percentage = ((double) filesRestored.get() / totalFiles) * 100;
          System.out.println("Restore Progress: " + String.format("%.2f", percentage) + "%");
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }).start();

      executorService.shutdown();
      executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      System.out.println("Restore complete!");
    } catch (IOException e) {
      System.out.println("Restore failed: " + e.getMessage());
      throw e;
    } catch (InterruptedException e) {
      System.out.println("Restore interrupted: " + e.getMessage());
      executorService.shutdown();
      Thread.currentThread().interrupt();
    }
  }
}
