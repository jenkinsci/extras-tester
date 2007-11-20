package hudson.model;

import hudson.scm.SubversionSCM;

import java.io.File;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;

public abstract class SubversionTestCase extends HudsonTestCase {
    protected File svnrepo;
    private String repositoryLocation;

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

        // make repository url platform independent
        repositoryLocation = getFileUrlProtocol() + svnrepo.getPath();
        String svnUrl = SVNURL.parseURIDecoded(repositoryLocation).toDecodedString();
                
        exec("svn", "co", svnUrl, svnwc.getPath());
        
        // For unit tests, this is required
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    /**
     * Creates an empty directory at the root of the SVN repository, and associates the Hudson project with it.
     * 
     * @param project
     * @param projectName
     * @return working copy directory
     */
    protected File createSubversionProject(FreeStyleProject project) {
        File projectDir = new File(svnwc, project.getName());
        exec("mkdir", projectDir.getPath());
        exec("svn", "add", projectDir.getPath());
        exec("svn", "commit", "-m", "newproject", projectDir.getPath());

        project.setScm(new SubversionSCM(new String[] { getFileUrlProtocol() + svnrepo + "/" + projectDir.getName() },
                new String[] { "." }, true, null));
        return projectDir;
    }

    /**
     * experimental
     * might need one more slash at end of protocol for windows
     * (untested on *nix) 
     * @return the proper protocol based on the OS
     */
	protected String getFileUrlProtocol() {
		String fileUrlProtocol = onMsftWindows()? "file:///" : "file://";
		return fileUrlProtocol;
	}

}
