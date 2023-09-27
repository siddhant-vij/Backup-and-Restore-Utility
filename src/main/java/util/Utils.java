package main.java.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Utils {
  public static void copyFile(Path src, Path dest) throws IOException {
    // Ensure parent directories exist
    if (dest.getParent() != null) {
      Files.createDirectories(dest.getParent());
    }
    // Copy file
    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
  }
}
