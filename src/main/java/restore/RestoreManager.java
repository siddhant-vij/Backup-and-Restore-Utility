package main.java.restore;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RestoreManager {

  private final Configuration config;

  public RestoreManager(Configuration config) {
    this.config = config;
  }

  public void restore() throws IOException {
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    AtomicLong bytesRestored = new AtomicLong(0);
    Path backupPath = Path.of(config.getDefaultBackupDir());
    Path restorePath = Path.of(config.getDefaultRestoreDir());

    List<Path> filesToRestore = new ArrayList<>();
    AtomicLong totalBytes = new AtomicLong(0);

    Files.walkFileTree(backupPath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            filesToRestore.add(file);
            totalBytes.addAndGet(attrs.size());
            return FileVisitResult.CONTINUE;
          }
        });

    long totalFiles = filesToRestore.size();
    System.out.println(
        "\nNo.of files to restore: " + totalFiles + " with disk space: " + totalBytes.get() / (1024 * 1024) + " MB");

    FileOperationsUtil.checkAndCreateDir(restorePath);
    FileOperationsUtil.displayProgress(bytesRestored, totalBytes.get());

    for (Path file : filesToRestore) {
      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
      Runnable restoreTask = () -> {
        try {
          String relativeFilePath = backupPath.relativize(file).toString();
          if (config.isEnableCompression() && relativeFilePath.endsWith(".zip")) {
            relativeFilePath = relativeFilePath.substring(0, relativeFilePath.length() - 4);
          }
          Path destFile = restorePath.resolve(relativeFilePath);
          Files.createDirectories(destFile.getParent());
          if (config.isEnableCompression()) {
            FileOperationsUtil.decompressFile(file, destFile);
          } else {
            FileOperationsUtil.copyFile(file, destFile);
          }
          bytesRestored.addAndGet(attrs.size());
        } catch (IOException e) {
          e.printStackTrace();
        }
      };
      executorService.submit(restoreTask);
    }

    executorService.shutdown();
    try {
      executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      System.out.println("\nRestore complete!");
    } catch (InterruptedException e) {
      System.out.println("\nRestore Interrupted!");
      e.printStackTrace();
    }
  }
}
