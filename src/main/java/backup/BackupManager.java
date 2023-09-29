package main.java.backup;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    long totalFiles = Files.walk(sourcePath).filter(Files::isRegularFile).count();
    FileOperationsUtil.checkAndCreateDir(backupDir);

    try {
      Files.walkFileTree(sourcePath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              try {
                Runnable backupTask = () -> {
                  try {
                    Path destFile = config.isEnableCompression()
                        ? Paths.get(backupDir.resolve(sourcePath.relativize(file)).toString() + ".zip")
                        : backupDir.resolve(sourcePath.relativize(file));
                    Files.createDirectories(destFile.getParent());
                    if (config.isEnableCompression()) {
                      FileOperationsUtil.compressFile(file, destFile);
                    } else {
                      FileOperationsUtil.copyFile(file, destFile);
                    }
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

      FileOperationsUtil.displayProgress(filesBackedUp, totalFiles);

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
