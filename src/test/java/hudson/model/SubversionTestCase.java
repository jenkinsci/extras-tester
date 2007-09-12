package hudson.model;

import hudson.scm.SubversionSCM;

import java.io.File;

import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;

public abstract class SubversionTestCase extends HudsonTestCase {
    protected File svnrepo;
    protected String repositoryLocation;

    protected File svnwc;

    @Override
    /**
     * Initializes the SVN repository and checks it out. Provides <tt>File svnrepo</tt> and <tt>File svnwc</tt> to
     * the test case.
     */
    protected void setUp() throws Exception {
        super.setUp();
        File tempdir = createTempDir("svn");
        svnrepo = new File(tempdir, "repo");
        svnrepo.mkdir();
        svnwc = new File(tempdir, "wc");
        svnwc.mkdir();
        exec("svnadmin", "create", svnrepo.getPath());
        repositoryLocation = "file://" + svnrepo.getPath();
        exec("svn", "co", repositoryLocation, svnwc.getPath());
        
        // For unit tests, this is required
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    /**
     * Creates an empty directory at the root of the SVN repository, and associates the Hudson project with it.
     * 
     * @param project
     * @param projectName
     * @return
     */
    protected File createSubversionProject(FreeStyleProject project) {
        File projectDir = new File(svnwc, project.getName());
        exec("mkdir", projectDir.getPath());
        exec("svn", "add", projectDir.getPath());
        exec("svn", "commit", "-m", "newproject", projectDir.getPath());

        project.setScm(new SubversionSCM(new String[] { "file://" + svnrepo + "/" + projectDir.getName() },
                new String[] { "." }, true, "user1", null));
        return projectDir;
    }
}
