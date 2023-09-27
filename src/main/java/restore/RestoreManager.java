package main.java.restore;

import main.java.config.Configuration;
import main.java.util.Utils;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class RestoreManager {

  private final Configuration config;

  public RestoreManager(Configuration config) {
    this.config = config;
  }

  public void restore() throws IOException {
    Path backupPath = Path.of(config.getDefaultBackupDir());
    Path restorePath = Path.of(config.getDefaultRestoreDir());

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
              Path restoreFile = restorePath.resolve(backupPath.relativize(file));
              Utils.copyFile(file, restoreFile);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      System.out.println("Restore failed: " + e.getMessage());
      throw e;
    }
  }
}
