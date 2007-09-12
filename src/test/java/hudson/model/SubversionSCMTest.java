package hudson.model;

import hudson.scm.SubversionSCM;

import java.io.File;
import java.util.logging.Logger;

/**
 * Tests for {@link SubversionSCM}. Requires a *nix machine with Subversion client and server installed.
 */
public class SubversionSCMTest extends SubversionTestCase {
    private static final Logger log = Logger.getLogger(SubversionSCMTest.class.getName());

    public void testRemotePathExistsWithHudsonAPI() throws Exception {
        assertFalse(new SubversionSCM(new String[] { "file://" + svnrepo + "/nonexistent" }, new String[] { "." },
                true, "user1", null).repositoryLocationsExist());
        assertTrue(new SubversionSCM(new String[] { "file://" + svnrepo }, new String[] { "." }, true, "user1", null)
                .repositoryLocationsExist());
    }

    public void testDeleteProject() {
        FreeStyleProject project = new FreeStyleProject(Hudson.getInstance(), "test");
        File projectDir = createSubversionProject(project);
        File hello = new File(projectDir, "hello");
        setCommand(project, "echo Hello World");

        Result result;

        result = build(project);
        assertSuccess(result);

        exec("touch", hello.getPath());
        exec("svn", "add", hello.getPath());
        exec("svn", "commit", "-m", "hello", hello.getPath());
        result = build(project);
        assertSuccess(result);

        exec("svn", "up", projectDir.getPath());
        exec("svn", "rm", projectDir.getPath());
        exec("svn", "commit", "-m", "deleted", projectDir.getPath());

        result = build(project);
        assertFailure(result);

        result = build(project);
        assertNull("Project should be disabled", result);
        assertTrue(project.disabled);
    }
}
