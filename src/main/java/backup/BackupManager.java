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
  private final int chunkSize = 20; // Change this & check performance!
  List<String> includePatterns;
  List<String> excludePatterns;
  private boolean enableIntegrityCheck;
  private String hashAlgorithm;
  private String hashFileDir;

  public BackupManager(Configuration config) {
    this.config = config;
    this.includePatterns = config.getBackupIncludePatterns();
    this.excludePatterns = config.getBackupExcludePatterns();
    this.enableIntegrityCheck = config.isEnableIntegrityCheck();
    this.hashAlgorithm = config.getHashAlgorithm();
    this.hashFileDir = config.getHashFileDir();
  }

  public void backup() throws IOException {
    if (config.isEnableEncryption()) {
      System.out.print("\nEnter password for encryption: ");
      encryptionPassword = new String(System.console().readPassword());
    }

    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    AtomicLong bytesBackedUp = new AtomicLong(0);
    Path sourcePath = Path.of(config.getDefaultSourceDir());
    Path backupDir = Path.of(config.getDefaultBackupDir());

    Queue<Path> filesToBackup = new ConcurrentLinkedQueue<>();
    AtomicLong totalBytes = new AtomicLong(0);

    Files.walkFileTree(sourcePath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (FileOperationsUtil.matchPattern(file.toString(), includePatterns) &&
                !FileOperationsUtil.matchPattern(file.toString(), excludePatterns)) {
              filesToBackup.add(file);
              totalBytes.addAndGet(attrs.size());
            }
            return FileVisitResult.CONTINUE;
          }
        });

    long totalFiles = filesToBackup.size();
    System.out.println(
        "\nNo. of files to backup: " + totalFiles + " with disk space: " + totalBytes.get() / (1024 * 1024) + " MB");
    FileOperationsUtil.checkAndCreateDir(backupDir);
    Timer timer = FileOperationsUtil.displayProgressBackup(bytesBackedUp, 2 * totalBytes.get());
    SecretKey aesKey = null;
    try {
      aesKey = KeyManagementUtil.generateAESKey(encryptionPassword);
    } catch (Exception e) {
      e.printStackTrace();
    }

    final SecretKey aesKeyFinal = aesKey;
    final ConcurrentHashMap<String, String> fileHashes = new ConcurrentHashMap<>();
    for (int i = 0; i < filesToBackup.size(); i += chunkSize) {
      int end = Math.min(i + chunkSize, filesToBackup.size());
      List<Path> tempFileList = new ArrayList<>(filesToBackup);
      List<Path> chunkFiles = tempFileList.subList(i, end);

      Runnable backupTask = () -> {
        try {
          FileOperationsUtil.createPartitionedBackup(chunkFiles, sourcePath, backupDir, config.isEnableCompression(),
              config.isEnableEncryption(), aesKeyFinal, bytesBackedUp, totalBytes, enableIntegrityCheck, hashAlgorithm,
              fileHashes);
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
      FileOperationsUtil.mergeTemporaryFilesIntoOne(backupDir.resolve("backup.zip"), tempZips,
          config.isEnableEncryption(), aesKey, bytesBackedUp, totalBytes, enableIntegrityCheck, fileHashes,
          hashFileDir);
      KeyManagementUtil.saveKeyToFile(aesKeyFinal, config.getAesFileKeyDir() + "/aes.key", encryptionPassword);
      System.out.println("\nBackup complete!");
      timer.cancel();
    } catch (InterruptedException e) {
      System.out.println("\nBackup Interrupted!");
      e.printStackTrace();
    } catch (Exception e) {
      System.out.println("\nSaving key to file failed!");
      e.printStackTrace();
    }
  }
}
