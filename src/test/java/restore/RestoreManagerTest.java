package test.java.restore;

import main.java.restore.RestoreManager;
import main.java.config.Configuration;
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public class RestoreManagerTest {
  private final Configuration config = new Configuration();
  private final RestoreManager restoreManager = new RestoreManager(config);

  // Happy path
  @Test
  public void testRestore_HappyPath() {
    Path backupDir = Paths.get("backup_test");
    Path restoreDir = Paths.get("restore_test");

    try {
      // Setup known backup directory structure
      Files.createDirectories(backupDir.resolve("subdir"));
      Files.writeString(backupDir.resolve("file1.txt"), "file1");
      Files.writeString(backupDir.resolve("subdir/file2.txt"), "file2");

      // Update the config to use the test directories
      config.setDefaultBackupDir(backupDir.toString());
      config.setDefaultRestoreDir(restoreDir.toString());

      // Perform the restore
      restoreManager.restore();

      // Validate restore
      assertTrue(Files.exists(restoreDir));
      assertTrue(Files.exists(restoreDir.resolve("file1.txt")));
      assertTrue(Files.exists(restoreDir.resolve("subdir/file2.txt")));

      String file1Content = Files.readString(restoreDir.resolve("file1.txt"));
      assertEquals("file1", file1Content);

      String file2Content = Files.readString(restoreDir.resolve("subdir/file2.txt"));
      assertEquals("file2", file2Content);

    } catch (IOException e) {
      fail("IOException was thrown: " + e.getMessage());
    } finally {
      // Cleanup
      deleteDirectory(backupDir);
      deleteDirectory(restoreDir);
    }
  }

  // Restore an empty directory
  @Test
  public void testRestore_EmptyDirectory() {
    Path backupDir = Paths.get("backup_empty_test");
    Path restoreDir = Paths.get("restore_empty_test");

    try {
      // Setup empty backup directory
      Files.createDirectories(backupDir);

      // Update the config
      config.setDefaultBackupDir(backupDir.toString());
      config.setDefaultRestoreDir(restoreDir.toString());

      // Perform the restore
      restoreManager.restore();

      // Validate restore - should also be empty
      assertTrue(Files.exists(restoreDir));
      assertTrue(Files.isDirectory(restoreDir));

    } catch (IOException e) {
      fail("IOException was thrown: " + e.getMessage());
    } finally {
      // Cleanup
      deleteDirectory(backupDir);
      deleteDirectory(restoreDir);
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
