package main.java.backup;

import main.java.config.Configuration;
import main.java.util.Utils;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class BackupManager {

  private final Configuration config;

  public BackupManager(Configuration config) {
    this.config = config;
  }

  public void backup() throws IOException {
    Path sourcePath = Path.of(config.getDefaultSourceDir());
    Path backupDir = Path.of(config.getDefaultBackupDir());

    // Explicitly create the backup directory if it doesn't exist
    Files.createDirectories(backupDir);

    try {
      Files.walkFileTree(sourcePath, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
              Path destFile = backupDir.resolve(sourcePath.relativize(file));
              Utils.copyFile(file, destFile);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      System.out.println("Backup failed: " + e.getMessage());
      throw e;
    }
  }
}
