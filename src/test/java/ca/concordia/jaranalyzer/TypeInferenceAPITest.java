package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.util.GitUtil;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Diptopol
 * @since 12/23/2020 9:54 PM
 */
public class TypeInferenceAPITest {

    @Test
    public void testGetQualifiedClassName() {
        List<String> qualifiedNameList = TypeInferenceAPI.getQualifiedClassName("AtomicLong");

        assert qualifiedNameList.size() == 1;
        assert "java.util.concurrent.atomic.AtomicLong".equals(qualifiedNameList.get(0));
    }

    @Test
    public void testLoadExternalJars() {
        String commitId = "b6e7262c1c4d0ef6ccafd3ed2a929ce0dbea860c";
        String projectName = "RefactoringMinerIssueReproduction";
        Path projectDirectory = Path.of("testProjectDirectory").resolve(projectName);

        Repository repository = GitUtil.getRepository(projectName,
                "https://github.com/diptopol/RefactoringMinerIssueReproduction.git", projectDirectory);

        TypeInferenceAPI.loadExternalJars(commitId, projectName, repository);
        TypeInferenceAPI.loadExternalJars(commitId, projectName, repository);

        List<String> qualifiedNameList = TypeInferenceAPI.getQualifiedClassName("Refactoring");

        assert qualifiedNameList.size() == 1;
        assert "org.refactoringminer.api.Refactoring".equals(qualifiedNameList.get(0));
    }
}
