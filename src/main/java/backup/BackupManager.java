package main.java.backup;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private final int chunkSize = 20; // Change this & check performance!

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
    FileOperationsUtil.displayProgressBackup(bytesBackedUp, 2 * totalBytes.get());

    for (int i = 0; i < filesToBackup.size(); i += chunkSize) {
      int end = Math.min(i + chunkSize, filesToBackup.size());
      List<Path> chunkFiles = filesToBackup.subList(i, end);

      Runnable backupTask = () -> {
        try {
          FileOperationsUtil.createPartitionedBackup(chunkFiles, sourcePath, backupDir, config.isEnableCompression(),
              bytesBackedUp, totalBytes);
        } catch (IOException e) {
          e.printStackTrace();
        }
      };
      executorService.submit(backupTask);
    }

    executorService.shutdown();
    try {
      executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      List<Path> tempZips = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "temp_*.zip")) {
        for (Path entry : stream) {
          tempZips.add(entry);
        }
      }
      FileOperationsUtil.mergeTemporaryFilesIntoOne(backupDir.resolve("backup.zip"), tempZips, bytesBackedUp,
          totalBytes);
      System.out.println("\nBackup complete!");
    } catch (InterruptedException e) {
      System.out.println("\nBackup Interrupted!");
      e.printStackTrace();
    }
  }
}
