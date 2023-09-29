// Test cases only applicable for Basic Functionality of Backup & Restore
// With every new feature added, instead of adding test cases for every feature,
// I've modified the basic test cases to adapt to the new feature.
// Not to be followed for real practical project - develop new test cases for new features

package test.java.restore;

import main.java.restore.RestoreManager;
import main.java.config.Configuration;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class RestoreManagerTest {
  private final Configuration config = new Configuration();
  private final RestoreManager restoreManager = new RestoreManager(config);

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  // Happy path
  @Test
  public void testRestore_HappyPath() {
    try {
      File backupDirFile = tempFolder.newFolder("backup_test");
      File restoreDirFile = tempFolder.newFolder("restore_test");
      Path backupDir = backupDirFile.toPath();
      Path restoreDir = restoreDirFile.toPath();

      Files.createDirectories(backupDir.resolve("subdir"));
      Files.writeString(backupDir.resolve("file1.txt"), "file1");
      Files.writeString(backupDir.resolve("subdir/file2.txt"), "file2");

      config.setDefaultBackupDir(backupDir.toString());
      config.setDefaultRestoreDir(restoreDir.toString());

      // Disable compression for this test
      config.setEnableCompression(false);

      restoreManager.restore();

      assertTrue(Files.exists(restoreDir));
      assertTrue(Files.exists(restoreDir.resolve("file1.txt")));
      assertTrue(Files.exists(restoreDir.resolve("subdir/file2.txt")));

      String file1Content = Files.readString(restoreDir.resolve("file1.txt"));
      assertEquals("file1", file1Content);

      String file2Content = Files.readString(restoreDir.resolve("subdir/file2.txt"));
      assertEquals("file2", file2Content);

    } catch (IOException e) {
      fail("IOException was thrown: " + e.getMessage());
    }
  }

  // Restore an empty directory
  @Test
  public void testRestore_EmptyDirectory() {
    try {
      // Create backup and restore directories
      File backupDir = tempFolder.newFolder("backup_empty_test");
      File restoreDir = tempFolder.newFolder("restore_empty_test");

      // Update the config
      config.setDefaultBackupDir(backupDir.getAbsolutePath());
      config.setDefaultRestoreDir(restoreDir.getAbsolutePath());

      // Perform the restore
      restoreManager.restore();

      // Validate restore - should also be empty
      assertTrue("Restore directory should exist", Files.exists(restoreDir.toPath()));
      assertTrue("Restore directory should be a directory", Files.isDirectory(restoreDir.toPath()));

    } catch (IOException e) {
      fail("IOException was thrown: " + e.getMessage());
    }
  }

  // Restore a non-existent backup
  @Test(expected = IOException.class)
  public void testRestore_NonExistentBackup() throws IOException {
    // Update the config to use a non-existent backup directory
    config.setDefaultBackupDir("nonexistent_backup");

    // Perform the restore
    restoreManager.restore();
  }

  // Insufficient permissions
  @Test(expected = AccessDeniedException.class)
  public void testRestore_InsufficientPermissions() throws IOException {
    // Update the config to use a directory without sufficient permissions
    config.setDefaultRestoreDir("C:\\\\Windows\\\\System32");

    // Perform the restore
    restoreManager.restore();
  }
}
