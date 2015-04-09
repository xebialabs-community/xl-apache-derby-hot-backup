# Hot Backup for Apache Derby DB for XL Release and XL Deploy

This project defines an XL Release and XL Deploy plugin which adds a way to make a hot backup in case the product runs on Apache Derby database (which comes with standard distribution).

## Installation

This plugin only works with _custom_ Jackrabbit repository configuration which you can find [in `jackrabbit-repository.xml` here](src/main/resources/sample/jackrabbit-repository.xml).

Note also that this plugin relies on standard repository path in your XL Release or XL Deploy: it has to be `XLR_or_XLD_HOME/repository/`.

The reason for custom Jackrabbit configuration is that by default attachment files are stored on filesystem and not in database. So a backup of standard configuration would not be complete.

To install the plugin you have to:

1. Download and copy the [jackrabbit-repository.xml](src/main/resources/sample/jackrabbit-repository.xml) into the `XLR_or_XLD_HOME/conf/` folder, replacing the default one.

  1.1. If you already have data in your repository, then you have to migrate it to the new Jackrabbit configuration. You can read [this blog post](http://blog.xebialabs.com/2015/04/07/migrate-xl-release-xl-deploy-repository-another-database/) to find out how. This is needed because in the default configuration of XL Release or XL Deploy all attachments are stored in local file system and not in the database.
2. Download the [`xl-apache-derby-hot-backup-1.2.jar`](https://github.com/xebialabs-community/xl-apache-derby-hot-backup/releases/download/v1.2/xl-apache-derby-hot-backup-1.2.jar) into `XLR_or_XLD_HOME/plugins/`.
3. Start the server.

## Usage

### Creating backup

The plugin adds a new REST endpoint which triggers a hot backup to be created: `POST /server/derby-backup?path=/backup/location`. The `path` query parameter specifies where the data will be backed up to. Write permissions are of course needed on that folder.

You can make the hot backup in XL Release using following `curl` statement, assuming XL Release is running on http://localhost:5516/my-xlr/:

    curl --user admin -X POST http://localhost:5516/my-xlr/server/derby-backup\?path\=/tmp/backups/$(date +"%Y-%m-%d_%H-%M-%S")
    
For XL Deploy it is almost the same:

    curl --user admin -X POST http://localhost:4516/my-xld/deployit/server/derby-backup\?path\=/tmp/backups/$(date +"%Y-%m-%d_%H-%M-%S")

This call will ask you for a password and save a backup in a new timestamp directory, e.g. `/tmp/backups/2015-04-08_17-29-10`. Only _admin_ users are allowed to create backups.

As a result of the call a folder `repository` will be created in specified directory, containing the Apache Derby database backup and some additional files needed to restore the repository.
 
### Restoring from backup

1. Stop XL Release or XL Deploy.
2. Rename or move current `XL_HOME/repository/` folder.
3. Copy the backed up folder into `XL_HOME`, e.g.: `cp -r /tmp/backups/2015-04-08_17-29-10/repository $XL_HOME/`.
4. Start the server.

  4.1. **Note** that Lucene indexes cannot be backed up, so they will be rebuilt from scratch when restoring from the backup. This may take hours if you have a large repository.
