package ca.concordia.jaranalyzer.util.artifactextraction;

import java.util.Objects;

/**
 * @author Diptopol
 * @since 3/12/2022 4:36 PM
 */
public class Artifact {

    private String groupId;
    private String artifactId;
    private String version;
    private String type;

    public Artifact(String groupId, String artifactId, String version, String type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact artifact = (Artifact) o;

        return groupId.equals(artifact.groupId) && artifactId.equals(artifact.artifactId) && version.equals(artifact.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }
}
