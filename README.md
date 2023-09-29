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

- **Backup Encryption**: Encrypt backup files for added security.
- **Selective Patterns**: Backup/Restore only specific files or directories.
- **Exclude Patterns**: Specify file or directory patterns to exclude from backup/restore.
- **Backup Integrity Checks**: Verify the integrity of backup files.
- **Incremental Backups**: Only back up files that have changed since the last (any) backup.
- **Differential Backups**: Only back up files that have changed since the last (full) backup.
- **Backup Versioning**: Save multiple versions of backup files.
- **Configuration Files**: Use configuration files to pre-set backup and restore settings.
- **Log Generation**: Generate logs detailing the backup and restore operations.
- **Backup Size Estimation**: Provide an estimate of the backup size before proceeding.
- **Backup to Remote Server**: Ability to backup to a remote server via FTP or SSH.
- **Scheduled Backups**: Ability to schedule backups at recurring intervals.
- **Bandwidth Throttling**: Limit the amount of network bandwidth used during backup to a remote server.
- **E-mail Notifications**: Send e-mail notifications upon successful or failed operations.
- **Chunked Backups**: Break up large files into smaller chunks for more efficient storage.
- **Functionality Rollback**: Ability to rollback a backup/restore operation if it fails partway.
- **Backup Templates**: Save and load backup configurations as templates.
- **Disk Space Checks**: Warn user if not enough disk space is available for backup.
- **Priority Backups**: Allow setting priority for specific files or directories in backup.
- **Data Deduplication**: Check for and eliminate duplicate data to save space.


## License

Distributed under the MIT License. See [`LICENSE`](https://github.com/siddhant-vij/Backup-and-Restore-Utility/blob/main/LICENSE) for more information.