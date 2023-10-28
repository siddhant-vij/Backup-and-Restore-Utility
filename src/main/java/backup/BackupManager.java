package main.java.backup;

import main.java.config.Configuration;
import main.java.util.FileOperationsUtil;
import main.java.util.KeyManagementUtil;

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
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.SecretKey;

public class BackupManager {

  private final Configuration config;
  private String encryptionPassword = null;
  private final int CHUNK_SIZE = 20; // Change this & check performance!

  public BackupManager(Configuration config) {
    this.config = config;
  }

  public void backup() throws IOException {
    initializeEncryption();
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    BackupFileData backupFileData = gatherFilesToBackupAndCalculateTotalBytes();
    Queue<Path> filesToBackup = backupFileData.filesToBackup();
    AtomicLong totalBytes = backupFileData.totalBytes();
    Path sourcePath = Path.of(config.getDefaultSourceDir());
    Path backupDir = Path.of(config.getDefaultBackupDir());
    boolean isEnoughSpace = FileOperationsUtil.checkDiskSpace(totalBytes.get(), backupDir);
    if (!isEnoughSpace) {
      System.exit(1);
    }
    AtomicLong bytesBackedUp = new AtomicLong(0);
    System.out.println("\nNo. of files to backup: " + filesToBackup.size());
    FileOperationsUtil.checkAndCreateDir(backupDir);
    Timer timer = FileOperationsUtil.displayProgressBackup(bytesBackedUp, 2 * totalBytes.get());
    SecretKey aesKey = initializeAESKey();
    final ConcurrentHashMap<String, String> fileHashes = new ConcurrentHashMap<>();
    executeBackupTasks(filesToBackup, totalBytes, sourcePath, backupDir, executorService, aesKey, fileHashes, timer,
        bytesBackedUp);
  }

  private void initializeEncryption() {
    if (config.isEnableEncryption()) {
      System.out.print("\nEnter password for encryption: ");
      encryptionPassword = new String(System.console().readPassword());
    }
  }

  private record BackupFileData(Queue<Path> filesToBackup, AtomicLong totalBytes) {
  }

  private BackupFileData gatherFilesToBackupAndCalculateTotalBytes() throws IOException {
    Queue<Path> filesToBackup = new ConcurrentLinkedQueue<>();
    AtomicLong totalBytes = new AtomicLong(0);
    Path sourcePath = Path.of(config.getDefaultSourceDir());
    Files.walkFileTree(sourcePath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (FileOperationsUtil.matchPattern(file.toString(), config.getBackupIncludePatterns())
                && !FileOperationsUtil.matchPattern(file.toString(), config.getBackupExcludePatterns())) {
              filesToBackup.add(file);
              totalBytes.addAndGet(attrs.size());
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return new BackupFileData(filesToBackup, totalBytes);
  }

  private SecretKey initializeAESKey() {
    SecretKey aesKey = null;
    try {
      aesKey = KeyManagementUtil.generateAESKey(encryptionPassword);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return aesKey;
  }

  private void executeBackupTasks(Queue<Path> filesToBackup, AtomicLong totalBytes, Path sourcePath, Path backupDir,
      ExecutorService executorService, SecretKey aesKey, ConcurrentHashMap<String, String> fileHashes, Timer timer,
      AtomicLong bytesBackedUp) {
    submitBackupTasks(filesToBackup, totalBytes, sourcePath, backupDir, executorService, aesKey, fileHashes,
        bytesBackedUp);
    waitForTaskCompletion(executorService);
    finalizeBackup(backupDir, aesKey, bytesBackedUp, totalBytes, fileHashes, timer);
  }

  private void submitBackupTasks(Queue<Path> filesToBackup, AtomicLong totalBytes, Path sourcePath, Path backupDir,
      ExecutorService executorService, SecretKey aesKey, ConcurrentHashMap<String, String> fileHashes,
      AtomicLong bytesBackedUp) {
    for (int i = 0; i < filesToBackup.size(); i += CHUNK_SIZE) {
      int end = Math.min(i + CHUNK_SIZE, filesToBackup.size());
      List<Path> tempFileList = new ArrayList<>(filesToBackup);
      List<Path> chunkFiles = tempFileList.subList(i, end);
      Runnable backupTask = () -> {
        try {
          FileOperationsUtil.createPartitionedBackup(chunkFiles, sourcePath, backupDir, config, aesKey, bytesBackedUp,
              totalBytes, fileHashes);
        } catch (IOException e) {
          e.printStackTrace();
        }
      };
      executorService.submit(backupTask);
    }
  }

  private void waitForTaskCompletion(ExecutorService executorService) {
    executorService.shutdown();
    try {
      executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      System.out.println("\nBackup Interrupted!");
      e.printStackTrace();
    }
  }

  private void finalizeBackup(Path backupDir, SecretKey aesKey, AtomicLong bytesBackedUp, AtomicLong totalBytes,
      ConcurrentHashMap<String, String> fileHashes, Timer timer) {
    try {
      List<Path> tempZips = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "temp_*.zip")) {
        for (Path entry : stream) {
          tempZips.add(entry);
        }
      }
      FileOperationsUtil.mergeTemporaryFilesIntoOne(backupDir.resolve("backup.zip"), tempZips, bytesBackedUp,
          fileHashes, config);
      KeyManagementUtil.saveKeyToFile(aesKey, config.getAesFileKeyDir() + "/aes.key", encryptionPassword);
      System.out.println("\nBackup complete!");
      timer.cancel();
    } catch (Exception e) {
      System.out.println("\nSaving key to file failed!");
      e.printStackTrace();
    }
  }
}
