// Test cases only applicable for Basic Functionality of Backup & Restore
// With every new feature added, instead of adding test cases for every feature,
// I've modified the basic test cases to adapt to the new feature.
// Not to be followed for real practical project - develop new test cases for new features

package test.java.backup;

import main.java.backup.BackupManager;
import main.java.config.Configuration;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class BackupManagerTest {
  private final Configuration config = new Configuration();
  private final BackupManager backupManager = new BackupManager(config);

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  // Happy path
  @Test
  public void testBackup_HappyPath() {
    try {
      File srcDirFile = tempFolder.newFolder("src_test");
      File destDirFile = tempFolder.newFolder("dest_test");
      Path srcDir = srcDirFile.toPath();
      Path destDir = destDirFile.toPath();

      // Setup and debug log
      Files.createDirectories(srcDir.resolve("subdir"));
      Files.writeString(srcDir.resolve("file1.txt"), "file1");
      Files.writeString(srcDir.resolve("subdir/file2.txt"), "file2");
      System.out.println("Before backup, srcDir contents: " + listDirContents(srcDir));

      // Update the config
      config.setDefaultSourceDir(srcDir.toString());
      config.setDefaultBackupDir(destDir.toString());

      // Disable compression for this test
      config.setEnableCompression(false);

      // Perform the backup
      backupManager.backup();

      // Debug log after backup
      System.out.println("After backup, destDir contents: " + listDirContents(destDir));

      assertTrue("Dest dir should exist", Files.exists(destDir));
      assertTrue("File1 should exist in dest dir", Files.exists(destDir.resolve("file1.txt")));
      assertTrue("File2 should exist in dest subdir", Files.exists(destDir.resolve("subdir/file2.txt")));

      String file1Content = Files.readString(destDir.resolve("file1.txt"));
      assertEquals("File1 content should match", "file1", file1Content);

      String file2Content = Files.readString(destDir.resolve("subdir/file2.txt"));
      assertEquals("File2 content should match", "file2", file2Content);

    } catch (IOException e) {
      e.printStackTrace();
      fail("IOException was thrown: " + e.getMessage());
    }
  }

  // Helper method for debugging
  private String listDirContents(Path dir) throws IOException {
    StringBuilder sb = new StringBuilder();
    Files.walk(dir).forEach(path -> sb.append(path.toString()).append("\n"));
    return sb.toString();
  }

  // Backup an empty directory
  @Test
  public void testBackup_EmptyDirectory() {
    try {
      // Create temporary directories for source and destination
      File srcDirFile = tempFolder.newFolder("src_empty_test");
      File destDirFile = tempFolder.newFolder("dest_empty_test");
      Path srcDir = srcDirFile.toPath();
      Path destDir = destDirFile.toPath();

      // Update the config
      config.setDefaultSourceDir(srcDir.toString());
      config.setDefaultBackupDir(destDir.toString());

      // Perform the backup
      backupManager.backup();

      // Validate backup - should also be empty
      assertTrue("Destination directory should exist", Files.exists(destDir));
      assertTrue("Destination directory should be a directory", Files.isDirectory(destDir));

    } catch (IOException e) {
      e.printStackTrace();
      fail("IOException was thrown: " + e.getMessage());
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
    config.setDefaultBackupDir("C:\\Windows\\System32");

    // Perform the backup
    backupManager.backup();
  }
}
