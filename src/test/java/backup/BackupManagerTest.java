package test.java.backup;

import main.java.backup.BackupManager;
import main.java.config.Configuration;
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public class BackupManagerTest {
  private final Configuration config = new Configuration();
  private final BackupManager backupManager = new BackupManager(config);

  // Happy path
  @Test
  public void testBackup_HappyPath() {
    Path srcDir = Path.of("src_test");
    Path destDir = Path.of("dest_test");

    try {
      // Setup known directory structure
      Files.createDirectories(srcDir.resolve("subdir"));
      Files.writeString(srcDir.resolve("file1.txt"), "file1");
      Files.writeString(srcDir.resolve("subdir/file2.txt"), "file2");

      // Update the config to use the test directories
      config.setDefaultSourceDir(srcDir.toString());
      config.setDefaultBackupDir(destDir.toString());

      // Perform the backup
      backupManager.backup();

      // Validate backup
      assertTrue("Dest dir should exist", Files.exists(destDir));
      assertTrue("File1 should exist in dest dir", Files.exists(destDir.resolve("file1.txt")));
      assertTrue("File2 should exist in dest subdir", Files.exists(destDir.resolve("subdir/file2.txt")));

      String file1Content = Files.readString(destDir.resolve("file1.txt"));
      assertEquals("File1 content should match", "file1", file1Content);

      String file2Content = Files.readString(destDir.resolve("subdir/file2.txt"));
      assertEquals("File2 content should match", "file2", file2Content);

    } catch (IOException e) {
      fail("IOException was thrown: " + e.getMessage());
    } finally {
      // Cleanup
      deleteDirectory(srcDir);
      deleteDirectory(destDir);
    }
  }

  // Backup an empty directory
  @Test
  public void testBackup_EmptyDirectory() {
    Path srcDir = Path.of("src_empty_test");
    Path destDir = Path.of("dest_empty_test");

    try {
      // Setup empty directory
      Files.createDirectories(srcDir);

      // Update the config
      config.setDefaultSourceDir(srcDir.toString());
      config.setDefaultBackupDir(destDir.toString());

      // Perform the backup
      backupManager.backup();

      // Validate backup - should also be empty
      assertTrue(Files.exists(destDir));
      assertTrue(Files.isDirectory(destDir));

    } catch (IOException e) {
      fail("IOException was thrown: " + e.getMessage());
    } finally {
      // Cleanup
      deleteDirectory(srcDir);
      deleteDirectory(destDir);
    }
  }

  // Backup a non-existent directory
  @Test(expected = IOException.class)
  public void testBackup_NonExistentDirectory() throws IOException {
    // Update the config to use a non-existent directory
    config.setDefaultSourceDir("nonexistent_dir");

    // Perform the backup
    backupManager.backup();
  }

  // Insufficient permissions (Windows-specific)
  @Test(expected = AccessDeniedException.class)
  public void testBackup_InsufficientPermissions() throws IOException {
    // Update the config to use a system directory
    config.setDefaultSourceDir("C:\\Windows\\System32");
    config.setDefaultBackupDir("C:\\Windows\\System32_backup");

    // Perform the backup
    backupManager.backup();
  }

  // Helper method to recursively delete a directory
  private static void deleteDirectory(Path path) {
    try {
      Files.walk(path)
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    } catch (IOException e) {
      // Ignore
    }
  }
}
