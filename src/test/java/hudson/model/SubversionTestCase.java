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
        repositoryLocation = getFileProtocolAndAbsolutePathStart() + svnrepo.getPath();
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

        project.setScm(new SubversionSCM(new String[] { getFileProtocolAndAbsolutePathStart() + svnrepo + "/" + projectDir.getName() },
                new String[] { "." }, true, null));
        return projectDir;
    }

    /**
     * return the file protocol and (if needed by OS) the character needed
     * to indicate we have an absolute path.  
     * 
     * More background provided by daniel dyer <danielwdyer@dsl.pipex.com>: 
     * The third slash is to normalise the file path so that it starts with a slash (e.g. /C:/Windows) to
     * indicate that it is an absolute path.  This isn't necessary on Linux because an absolute path already begins with a
     * slash.
     * It's not really part of the protocol part of the URL, which is clearer if you consider that file:///C:/Windows is
	 * shorthand for file://localhost/C:/Windows.
     *  
     * @return the proper protocol and root dir based on the OS
     */
	protected String getFileProtocolAndAbsolutePathStart() {
		final String fileUrl = "file://";
		String rootDir = onMsftWindows()? "/" : "";
		return fileUrl + rootDir;
	}

}
