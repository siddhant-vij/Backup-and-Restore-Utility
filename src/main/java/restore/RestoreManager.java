package main.java.restore;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
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

    long totalFiles = Files.walk(backupPath).filter(Files::isRegularFile).count();
    FileOperationsUtil.checkAndCreateDir(restorePath);

    try {
      Files.walkFileTree(backupPath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              try {
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

      FileOperationsUtil.displayProgress(filesRestored, totalFiles);

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
