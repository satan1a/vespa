// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;

import java.io.IOException;

/**
 * Metadata about an application package.
 *
 * @author hmusum
 */
public class ApplicationMetaData {

    private final String deployedByUser;
    private final String deployedFromDir;
    private final long deployTimestamp;
    private final boolean internalRedeploy;
    private final ApplicationId applicationId;
    private final String checksum;
    private final long generation;
    private final long previousActiveGeneration;

    public ApplicationMetaData(String deployedByUser, String deployedFromDir, Long deployTimestamp, boolean internalRedeploy,
                               ApplicationId applicationId, String checksum, Long generation, long previousActiveGeneration) {
        this.deployedByUser = deployedByUser;
        this.deployedFromDir = deployedFromDir;
        this.deployTimestamp = deployTimestamp;
        this.internalRedeploy = internalRedeploy;
        this.applicationId = applicationId;
        this.checksum = checksum;
        this.generation = generation;
        this.previousActiveGeneration = previousActiveGeneration;
    }

    /**
     * Gets the user who deployed the application.
     * Will return null if a problem occurred while getting metadata
     *
     * @return user name for the user who ran "deploy-application"
     */
    public String getDeployedByUser() {
        return deployedByUser;
    }

    /**
     * Gets the directory where the application was deployed from.
     * Will return null if a problem occurred while getting metadata
     *
     * @return path to raw deploy directory (for the original application)
     */
    public String getDeployPath() { return deployedFromDir; }

    public ApplicationId getApplicationId() { return applicationId; }

    /**
     * Gets the time the application was deployed.
     * Will return null if a problem occurred while getting metadata.
     *
     * @return when this application version was deployed in epoch ms
     */
    public Long getDeployTimestamp() { return deployTimestamp; }

    /**
     * Returns the config generation of this application instance.
     * Will return null if a problem occurred while getting metadata.
     */
    public Long getGeneration() { return generation; }

    /**
     * Returns whether this application generation was produced by a system internal redeployment,
     * not an application package change
     */
    public boolean isInternalRedeploy() { return internalRedeploy; }

    /** Returns an md5 hash of the contents of the application package */
    public String getChecksum() { return checksum; }

    /** Returns the previously active generation at the point when this application was created. */
    public long getPreviousActiveGeneration() { return previousActiveGeneration; }

    @Override
    public String toString() {
        return deployedByUser + ", " + deployedFromDir + ", " + deployTimestamp + ", " + generation + ", " +
               checksum + ", " + previousActiveGeneration;
    }

    public static ApplicationMetaData fromJsonString(String jsonString) {
        try {
            Slime data = SlimeUtils.jsonToSlime(jsonString);
            Inspector root = data.get();
            Inspector deploy = root.field("deploy");
            Inspector app = root.field("application");

            return new ApplicationMetaData(deploy.field("user").asString(),
                                           deploy.field("from").asString(),
                                           deploy.field("timestamp").asLong(),
                                           booleanField("internalRedeploy", false, deploy),
                                           ApplicationId.fromSerializedForm(app.field("id").asString()),
                                           app.field("checksum").asString(),
                                           app.field("generation").asLong(),
                                           app.field("previousActiveGeneration").asLong());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing json metadata", e);
        }
    }

    public Slime getSlime() {
        Slime slime = new Slime();
        Cursor meta = slime.setObject();
        Cursor deploy = meta.setObject("deploy");
        deploy.setString("user", deployedByUser);
        deploy.setString("from", deployedFromDir);
        deploy.setLong("timestamp", deployTimestamp);
        deploy.setBool("internalRedeploy", internalRedeploy);
        Cursor app = meta.setObject("application");
        app.setString("id", applicationId.serializedForm());
        app.setString("checksum", checksum);
        app.setLong("generation", generation);
        app.setLong("previousActiveGeneration", previousActiveGeneration);
        return slime;
    }

    private static boolean booleanField(String fieldName, boolean defaultValue, Inspector object) {
        Inspector value = object.field(fieldName);
        if ( ! value.valid()) return defaultValue;
        return value.asBool();
    }

    public String asJsonString() {
        return Utf8.toString(asJsonBytes());
    }

    public byte[] asJsonBytes() {
        try {
            return SlimeUtils.toJsonBytes(getSlime());
        } catch (IOException e) {
            throw new RuntimeException("Unable to encode metadata", e);
        }
    }

}
