package test.java.util;

import org.junit.*;
import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import main.java.util.Utils;
import main.java.config.Configuration;

public class UtilsTest {
  private final Configuration config = new Configuration();

  // Happy path Test
  @Test
  public void testCopyFile() {
    Path srcDir = Path.of(config.getDefaultSourceDir());
    Path src = srcDir.resolve("TestFile.txt");
    Path destDir = Path.of(config.getDefaultBackupDir());
    Path dest = destDir.resolve("TestFile.txt");

    try {
      // Ensure source and destination directories exist
      Files.createDirectories(srcDir);
      Files.createDirectories(destDir);

      // Create a source file
      Files.writeString(src, "Hello, world!");

      // Copy the file
      Utils.copyFile(src, dest);

      // Validate that the file was copied correctly
      String copiedContent = Files.readString(dest);
      assertEquals("Hello, world!", copiedContent);

      // Delete both the files
      Files.delete(src);
      Files.delete(dest);
    } catch (IOException e) {
      fail("An IOException was thrown: " + e.getMessage());
    }
  }

  // Source file does not exist
  @Test(expected = NoSuchFileException.class)
  public void testCopyFile_SourceNotExist() throws IOException {
    Path src = Path.of("nonexistentfile.txt");
    Path dest = Path.of("someParentDir", "destfile.txt");
    Utils.copyFile(src, dest);
  }

  // Destination directory does not exist, but should be created by Utils.copyFile
  @Test
  public void testCopyFile_DestDirNotExist() throws IOException {
    Path src = Path.of("sourcefile.txt");
    Path dest = Path.of("nonexistentdir", "destfile.txt");
    Files.writeString(src, "test");

    Utils.copyFile(src, dest);

    // Verify that the destination file exists and has the correct content
    assertTrue("Destination file should exist", Files.exists(dest));
    assertEquals("File content should match", "test", Files.readString(dest));

    // Cleanup
    Files.delete(src);
    Files.delete(dest);
    Files.delete(dest.getParent());
  }

  // Insufficient permissions (Windows-specific, need to be adjusted for Linux)
  @Test(expected = AccessDeniedException.class)
  public void testCopyFile_InsufficientPermissions() throws IOException {
    Path src = Path.of("sourcefile.txt");
    Path dest = Path.of("C:\\\\Windows\\\\System32\\\\destfile.txt");
    // trying to write to System32 should fail for a regular user
    Files.writeString(src, "test");
    Utils.copyFile(src, dest);
  }
}
