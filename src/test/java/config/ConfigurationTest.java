package test.java.config;

import main.java.config.Configuration;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ConfigurationTest {
  private Path tempConfigFilePath;
  
  @Before
  public void setUp() throws IOException {
    tempConfigFilePath = Files.createTempFile("temp-config", ".json");
  }

  // Happy path
  @Test
  public void testLoadConfig_HappyPath() throws IOException, URISyntaxException, ParseException {
    // Prepare a well-formed JSON string
    String jsonString = "{\"defaultSourceDir\":\"source\",\"defaultBackupDir\":\"backup\",\"defaultRestoreDir\":\"restore\"}";

    // Parse the string into a JSONObject
    JSONObject json = (JSONObject) new JSONParser().parse(jsonString);
    writeJSON(json);

    // Re-initialize the Configuration object
    Configuration config = new Configuration(tempConfigFilePath.toString());

    // Assert
    assertEquals("source", config.getDefaultSourceDir());
    assertEquals("backup", config.getDefaultBackupDir());
    assertEquals("restore", config.getDefaultRestoreDir());
  }

  // Malformed JSON
  @Test(expected = IOException.class)
  public void testLoadConfig_MalformedJSON() throws IOException {
    // Write a malformed JSON string
    Files.writeString(Path.of("path/to/default-config.json"), "malformed json");

    // This should throw an IOException
    new Configuration(tempConfigFilePath.toString());
  }

  // Missing fields
  @Test
  public void testLoadConfig_MissingFields() throws IOException, URISyntaxException, ParseException {
    // Prepare a well-formed JSON string
    String jsonString = "{\"defaultSourceDir\":\"source\"}";
    // Parse the string into a JSONObject
    JSONObject json = (JSONObject) new JSONParser().parse(jsonString);
    writeJSON(json);

    // Create a new Configuration object
    Configuration config = new Configuration(tempConfigFilePath.toString());

    // Check that the fields are not initialized as expected
    assertNotNull(config.getDefaultSourceDir());
    assertNull(config.getDefaultBackupDir());
    assertNull(config.getDefaultRestoreDir());
  }

  // Utility method to write JSON to the default-config.json
  private void writeJSON(JSONObject json) throws IOException, URISyntaxException {
    // Define the path to default-config.json. This should match the path read by
    // your Configuration class.
    Files.writeString(tempConfigFilePath, json.toJSONString());
  }
}
