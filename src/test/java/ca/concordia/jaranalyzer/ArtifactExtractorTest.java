package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractor;
import ca.concordia.jaranalyzer.artifactextractor.ArtifactExtractorResolver;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.util.GitUtil;
import ca.concordia.jaranalyzer.models.JarInfo;
import ca.concordia.jaranalyzer.util.Utility;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

/**
 * @author Diptopol
 * @since 12/27/2020 5:55 PM
 */
public class ArtifactExtractorTest {

    @Test
    public void testGetDependentArtifactSetFromEffectivePOM() {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction",
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> jarArtifactInfoSet = extractor.getDependentArtifactSet();

        assert jarArtifactInfoSet.size() == 1;

        Artifact jarArtifactInfo = jarArtifactInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarArtifactInfo.getGroupId())
                && "refactoring-miner".equals(jarArtifactInfo.getArtifactId())
                && "2.0.2".equals(jarArtifactInfo.getVersion());
    }

    @Test
    public void testGetJarInfo() {
        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction",
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git");

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> dependentArtifactSet = extractor.getDependentArtifactSet();

        assert dependentArtifactSet.size() == 1;

        Artifact artifact = dependentArtifactSet.iterator().next();

        Set<JarInfo> jarInfoSet = Utility.getJarInfoSet(artifact);

        assert jarInfoSet.size() == 1;

        JarInfo jarInfo = jarInfoSet.iterator().next();

        assert "com.github.tsantalis".equals(jarInfo.getArtifact().getGroupId())
                && "refactoring-miner".equals(jarInfo.getArtifact().getArtifactId())
                && "2.0.2".equals(jarInfo.getArtifact().getVersion());
    }

    @Test
    public void testGenerateEffectivePOMFromRepository() {
        String projectName = "RefactoringMinerIssueReproduction";
        Path pathToProject = Utility.getProjectPath(projectName);

        Git git = GitUtil.openRepository(projectName,
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git", pathToProject);

        ArtifactExtractorResolver extractorResolver = new ArtifactExtractorResolver("b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c",
                "RefactoringMinerIssueReproduction", git);

        ArtifactExtractor extractor = extractorResolver.getArtifactExtractor();
        Set<Artifact> dependentArtifactSet = extractor.getDependentArtifactSet();

        Artifact artifact = dependentArtifactSet.iterator().next();

        assert "com.github.tsantalis".equals(artifact.getGroupId())
                && "refactoring-miner".equals(artifact.getArtifactId())
                && "2.0.2".equals(artifact.getVersion());
    }

}