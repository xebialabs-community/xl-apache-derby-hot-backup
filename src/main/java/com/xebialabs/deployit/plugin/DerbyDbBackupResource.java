/**
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS
 * FOR A PARTICULAR PURPOSE. THIS CODE AND INFORMATION ARE NOT SUPPORTED BY XEBIALABS.
 */
package com.xebialabs.deployit.plugin;

import java.io.File;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import com.google.common.io.Files;

import com.xebialabs.deployit.checks.Checks;
import com.xebialabs.deployit.engine.spi.exception.DeployitException;
import com.xebialabs.deployit.security.PermissionDeniedException;
import com.xebialabs.deployit.security.PermissionEnforcer;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.xebialabs.deployit.security.permission.PlatformPermissions.ADMIN;
import static java.lang.String.format;

@Path("/server/derby-backup")
@Produces({MediaType.APPLICATION_JSON})
@Controller
public class DerbyDbBackupResource {

    private static final Logger logger = LoggerFactory.getLogger(DerbyDbBackupResource.class);

    private static final String DERBY_JDBC_URL_TEMPLATE = "jdbc:derby:%s;create=false";
    private static final String DEFAULT_DB = "repository/db";
    private static final String ARCHIVE_DB = "archive/db";
    private static final String[] WORKSPACE_XML_LOCATIONS = new String[] {
            "repository/workspaces/default/workspace.xml",
            "repository/workspaces/security/workspace.xml"
    };

    private PermissionEnforcer permissionEnforcer;

    @Autowired
    public DerbyDbBackupResource(PermissionEnforcer permissionEnforcer) {
        this.permissionEnforcer = permissionEnforcer;
    }

    @POST
    public void backup(@QueryParam("path") String backupPath, @DefaultValue("repository") @QueryParam("repo") String repository) {
        if (!permissionEnforcer.hasLoggedInUserPermission(ADMIN)) {
            throw PermissionDeniedException.forPermission(ADMIN, (String) null);
        }

        if (isNullOrEmpty(backupPath)) {
            throw new Checks.MissingArgumentException("path");
        }

        String repo = DEFAULT_DB;
        if (repository.equalsIgnoreCase("repository")) {
            repo = DEFAULT_DB;
        }
        if (repository.equalsIgnoreCase("archive")) {
            repo = ARCHIVE_DB;
        }

        File folder = new File(backupPath, "repository");
        if (folder.exists()) {
            throw new Checks.IncorrectArgumentException(format("There seems to already be an existing backup in directory [%s], " +
                    "please specify a different directory", folder));
        }
        if (!folder.mkdirs()) {
            throw new Checks.IncorrectArgumentException(format("Could not create backup directory [%s], " +
                    "please make sure the user running XL Release process has enough access rights", folder));
        }

        String url = format(DERBY_JDBC_URL_TEMPLATE, repo);
        logger.info("Starting backup of Derby database " + url);
        try (
                Connection connection = DriverManager.getConnection(url, "", "");
                CallableStatement statement = connection.prepareCall("CALL SYSCS_UTIL.SYSCS_BACKUP_DATABASE(?)")
        ) {
            // Per Derby documentation paths are required with forward slash
            statement.setString(1, folder.getAbsolutePath().replaceAll("\\\\", "/"));
            statement.execute();
        } catch (SQLException e) {
            throw new DeployitException(e, "Could not create a backup of Derby database");
        }

        if (repo.equals(DEFAULT_DB)) {
            logger.info("Database backup is done, copying additional files");
            for (String workspaceXml : WORKSPACE_XML_LOCATIONS) {
                File xml = new File(workspaceXml);
                if (!xml.exists()) {
                    throw new DeployitException("Could not find workspace XML file at " + xml);
                }
                File xmlBackup = new File(backupPath, workspaceXml);
                if (!xmlBackup.getParentFile().exists() && !xmlBackup.getParentFile().mkdirs()) {
                    throw new Checks.IncorrectArgumentException("Could not create folders for " + xmlBackup);
                }
                try {
                    Files.copy(xml, xmlBackup);
                } catch (IOException e) {
                    throw new DeployitException(e, format("Could not copy workspace XML file %s to %s", xml, xmlBackup));
                }
            }
        }

        logger.info("Backup is ready at " + folder.getParent());
    }
}