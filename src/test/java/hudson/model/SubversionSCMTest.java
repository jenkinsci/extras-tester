package hudson.model;

import hudson.scm.SubversionSCM;

import java.io.File;

/**
 * Tests for {@link SubversionSCM}. Requires a *nix machine with Subversion client and server installed.
 */
public class SubversionSCMTest extends SubversionTestCase {
    public void testSubversionSCM() {
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
    }
}
