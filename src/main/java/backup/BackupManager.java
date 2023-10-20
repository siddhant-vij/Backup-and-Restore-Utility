package main.java.backup;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class BackupManager {

  private final Configuration config;

  public BackupManager(Configuration config) {
    this.config = config;
  }

  public void backup() throws IOException {
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    AtomicLong bytesBackedUp = new AtomicLong(0);
    Path sourcePath = Path.of(config.getDefaultSourceDir());
    Path backupDir = Path.of(config.getDefaultBackupDir());

    List<Path> filesToBackup = new ArrayList<>();
    AtomicLong totalBytes = new AtomicLong(0);

    Files.walkFileTree(sourcePath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            filesToBackup.add(file);
            totalBytes.addAndGet(attrs.size());
            return FileVisitResult.CONTINUE;
          }
        });

    long totalFiles = filesToBackup.size();
    System.out.println(
        "\nNo.of files to backup: " + totalFiles + " with disk space: " + totalBytes.get() / (1024 * 1024) + " MB");
    FileOperationsUtil.checkAndCreateDir(backupDir);
    FileOperationsUtil.displayProgress(bytesBackedUp, totalBytes.get());

    for (Path file : filesToBackup) {
      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
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
          bytesBackedUp.addAndGet(attrs.size());
        } catch (IOException e) {
          e.printStackTrace();
        }
      };
      executorService.submit(backupTask);
    }

    executorService.shutdown();
    try {
      executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      System.out.println("\nBackup complete!");
    } catch (InterruptedException e) {
      System.out.println("\nBackup Interrupted!");
      e.printStackTrace();
    }
  }
}
