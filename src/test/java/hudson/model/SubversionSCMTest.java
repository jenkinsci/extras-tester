package hudson.model;

import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.SubversionSCM;

import java.io.File;
import java.util.logging.Logger;

/**
 * Tests for {@link SubversionSCM}. Requires Subversion client and server to be installed.
 */
public class SubversionSCMTest extends SubversionTestCase {
    private static final Logger log = Logger.getLogger(SubversionSCMTest.class.getName());

    SubversionRepositoryBrowser browser; 
    
    @Override
	protected void setUp() throws Exception {
		super.setUp();
		
		// Without mocking, not sure how to get a stapler request in a headless test, 
		// so pass in a null browser since not used in the test. 
	    // RepositoryBrowsers.createInstance(SubversionRepositoryBrowser.class, req, "svn.browser")
		browser = null;
	}

	public void testRemotePathExistsWithHudsonAPI() throws Exception {
        assertFalse(new SubversionSCM(new String[] { getFileProtocolAndAbsolutePathStart() + svnrepo + "/nonexistent" }, new String[] { "." },
                true, browser).repositoryLocationsExist());
        assertTrue(new SubversionSCM(new String[] { getFileProtocolAndAbsolutePathStart() + svnrepo }, new String[] { "." }, true, browser)
                .repositoryLocationsExist());
    }

    public void testDeleteProject() throws Exception {
        FreeStyleProject project = new FreeStyleProject(Hudson.getInstance(), "test");
        File projectDir = createSubversionProject(project);
        svnCommit("Add projects");
        setCommand(project, "echo Hello World");

        Result result;

        result = build(project).getResult();
        assertSuccess(result);

        File hello = new File(projectDir, "hello");
        exec("touch", hello.getPath());
        exec("svn", "add", hello.getPath());
        exec("svn", "commit", "-m", "hello", hello.getPath());
        result = build(project).getResult();
        assertSuccess(result);

        exec("svn", "up", projectDir.getPath());
        exec("svn", "rm", projectDir.getPath());
        exec("svn", "commit", "-m", "deleted", projectDir.getPath());

        result = build(project).getResult();
        assertFailure(result);

        Build build = build(project);
        assertNull("Project should be disabled", build);
        assertTrue(project.disabled);
    }
}
