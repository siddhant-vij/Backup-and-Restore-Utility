package main.java.cli;

import java.io.IOException;
import java.util.Scanner;

import main.java.backup.BackupManager;
import main.java.config.Configuration;
import main.java.restore.RestoreManager;

public class CommandLineInterface {

  public void run() {
    Configuration config = new Configuration();
    BackupManager backupManager = new BackupManager(config);
    RestoreManager restoreManager = new RestoreManager(config);
    Scanner scanner = new Scanner(System.in);
    int choice;

    do {
      // Display menu
      System.out.println("\nBackup and Restore Utility\n");
      System.out.println("1: Backup files");
      System.out.println("2: Restore files");
      System.out.println("3: Show configuration");
      System.out.println("0: Exit");
      System.out.print("Enter your choice: ");

      choice = scanner.nextInt();

      switch (choice) {
        case 1:
          try {
            final long startTime = System.currentTimeMillis();
            backupManager.backup();
            final long endTime = System.currentTimeMillis();
            System.out.println("It took " + (endTime - startTime) / 1000 + "s to finish backing up the files.\n");
          } catch (IOException e) {
            e.printStackTrace();
          }
          break;
        case 2:
          try {
            final long startTime = System.currentTimeMillis();
            restoreManager.restore();
            final long endTime = System.currentTimeMillis();
            System.out.println("It took " + (endTime - startTime) / 1000 + "s to finish restoring the files.\n");
          } catch (IOException e) {
            e.printStackTrace();
          }
          break;
        case 3:
          config.print();
          break;
        case 0:
          System.out.println("\nExiting...");
          break;
        default:
          System.out.println("Invalid choice. Please try again.");
      }
    } while (choice != 0);

    scanner.close();
  }
}
