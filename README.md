# Backup and Restore Utility

A simple terminal-based Backup & Restore Utility in Java. This tool allows the users to create and restore backups of files easily.

## Table of Contents

1. [Features](#features)
1. [Contributing](#contributing)
1. [Future Improvements](#future-improvements)
1. [License](#license)

## Features

- **Basic File Backup**: Ability to backup specified files and directories.
- **Basic File Restore**: Ability to restore from a backup.
- **Basic Test Suite**: Test suite for basic functionality.
- **Multi-threading**: Utilize multiple threads for faster backup and restore operations.
- **Backup Compression**: Compress the backup files to save space.
- **Backup & File Size**: Provide backup size & file count before proceeding.
- **Chunking -> Merging**: Break up into smaller chunks (for concurrency) & merge them together.
- **Single Backup File**: Create a single backup file, maintaining folder structure.
- **Encryption Decryption**: Encrypt & decrypt backup files for added security.
- **Inclusion/Exclusion**: Specify file patterns to include/exclude from backup & restore.
- **Backup Integrity Checks**: Verify the integrity of backup & restore files.

## Contributing

Contributions are what make the open-source community such an amazing place to learn, inspire, and create. Any contributions you make are **greatly appreciated**.

1. **Fork the Project**
2. **Create your Feature Branch**: 
    ```bash
    git checkout -b feature/AmazingFeature
    ```
3. **Commit your Changes**: 
    ```bash
    git commit -m 'Add some AmazingFeature'
    ```
4. **Push to the Branch**: 
    ```bash
    git push origin feature/AmazingFeature
    ```
5. **Open a Pull Request**

## Future Improvements

- **Incremental Backups**: Only back up files that have changed since the last (any) backup.
- **Differential Backups**: Only back up files that have changed since the last (full) backup.
- **Log Generation**: Generate logs detailing the backup and restore operations.
- **Code Refactoring**: Refactor the codebase to improve readability and maintainability.
- **Disk Space Checks**: Warn user if not enough disk space is available for backup.
- **Priority Backups**: Allow setting priority for specific files or directories in backup.
- **Data Deduplication**: Check for and eliminate duplicate data to save space.
- **Backup Versioning**: Save multiple versions of backup file (as soon as it is modified).
- **Commit/Rollback Mgmt.**: Offer transaction mechanism to guarantee backup & restore integrity.
- **API Integration**: Allow integration with other systems - used as a service by other apps.
- **Backup to Remote Server**: Ability to backup to a remote server via FTP or SSH.
- **Scheduled Backups**: Ability to schedule backups at recurring intervals.
- **Hot Backup/Quick Restore**: Ability to backup & restore with minimum downtime - time efficient.
- **Bandwidth Throttling**: Limit the amount of network bandwidth used during backup to a remote server.
- **Notification & Alerts**: Send status reports, success/failure notifications, and alerts.
- **Backup Templates**: Save and load backup configurations as templates.
- **User Access Control**: Restrict who can perform backup/restore to prevent unauthorized data access.
- **Multi-Platform Support**: Ability to backup data from various operating systems, databases & apps.
- **Cross-Platform Restore**: Ability to restore data to different hardware or operating systems.

## License

Distributed under the MIT License. See [`LICENSE`](https://github.com/siddhant-vij/Backup-and-Restore-Utility/blob/main/LICENSE) for more information.