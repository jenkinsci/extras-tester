package hudson.model;

import hudson.scm.SubversionSCM;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;

import antlr.ANTLRException;

public abstract class SubversionTestCase extends HudsonTestCase {
	protected File svnrepo;
	private String repositoryLocation;
	protected File svnwc;

	/**
	 * cache of uncommitted changes. 
	 */
	protected List<String> uncommittedChanges = new ArrayList<String>();
	
	
	@Override
	/**
	 * Initializes the SVN repository and checks it out. Provides
	 * <tt>File svnrepo</tt> and <tt>File svnwc</tt> to the test case.
	 */
	protected void setUp() throws Exception {
		super.setUp();
		File tempdir = createTempDir("hudson-svntest");
		svnrepo = new File(tempdir, "repo");
		svnrepo.mkdir();
		svnwc = new File(tempdir, "wc");
		svnwc.mkdir();
		exec("svnadmin", "create", "--pre-1.5-compatible", svnrepo.getPath());

		// make repository url platform independent
		repositoryLocation = getFileProtocolAndAbsolutePathStart()
				+ svnrepo.getPath();
		String svnUrl = SVNURL.parseURIDecoded(repositoryLocation)
				.toDecodedString();

		exec("svn", "co", svnUrl, svnwc.getPath());

		// For unit tests, this is required
		SVNRepositoryFactoryImpl.setup();
		FSRepositoryFactory.setup();
	}

	/**
	 * Creates an empty directory at the root of the SVN repository, and
	 * associates the Hudson project with it.
	 * 
	 * @param project
	 * @param projectName
	 * @return working copy directory
	 */
	protected File createSubversionProject(FreeStyleProject project) throws IOException {
		File projectDir = new File(svnwc, project.getName());
		exec("mkdir", projectDir.getPath());
		svnAdd(projectDir);

		project.setScm(new SubversionSCM(
				new String[] { getFileProtocolAndAbsolutePathStart() + svnrepo
						+ "/" + projectDir.getName() }, new String[] { "." },
				true, null));
		return projectDir;
	}

	/**
	 * return the file protocol and (if needed by OS) the character needed to
	 * indicate we have an absolute path.
	 * 
	 * More background provided by daniel dyer <danielwdyer@dsl.pipex.com>: The
	 * third slash is to normalise the file path so that it starts with a slash
	 * (e.g. /C:/Windows) to indicate that it is an absolute path. This isn't
	 * necessary on Linux because an absolute path already begins with a slash.
	 * It's not really part of the protocol part of the URL, which is clearer if
	 * you consider that file:///C:/Windows is shorthand for
	 * file://localhost/C:/Windows.
	 * 
	 * @return the proper protocol and root dir based on the OS
	 */
	protected String getFileProtocolAndAbsolutePathStart() {
		final String fileUrl = "file://";
		String rootDir = onMsftWindows() ? "/" : "";
		return fileUrl + rootDir;
	}

	protected File createNonpollingSubversionProject(FreeStyleProject project)
	throws ANTLRException, IOException {
		File wcProject = createSubversionProject(project);
		triggerSubversionPolling(project, false);
		return wcProject; 
	}

	protected File createPollingSubversionProject(FreeStyleProject project)
	throws ANTLRException, IOException {
		File wcProject = createSubversionProject(project);
		triggerSubversionPolling(project, true);
		return wcProject; 
	}

	@SuppressWarnings("unchecked")
	protected Trigger triggerSubversionPolling(FreeStyleProject project, boolean startImmediately)
			throws ANTLRException, IOException {
		final String EVERY_SECOND = "* * * * *";
		Trigger t1 = new SCMTrigger(EVERY_SECOND);
		project.addTrigger(t1);
		if (startImmediately) { 
			t1.start(project, false);
		}
		return t1;
	}

	/** 
	 * add file to svn without committing. 
	 * @param file the file
	 */
	public void svnAdd(File file) throws IOException {
		exec("svn", "add", file.getPath());
		svnCache(file);
	}

	/** 
	 * add file to svn
	 * @param file the file
	 * @param comment checkin comment
	 */
	public void svnAdd(File file, String comment) throws IOException {
		svnAdd(file);
        svnCommit(file, comment);
	}

	/** 
	 * cached changed file for svn, but don't commit
	 * @param file the file
	 */
	public void svnCache(File file) {
		uncommittedChanges.add(file.getPath());
	}

	/** 
	 * commit changed file to svn
	 * @param file the file
	 * @param comment checkin comment
	 */
	public void svnCommit(File file, String comment) throws IOException {
		String[] cmd = {"svn", "commit", "-m", comment, file.getPath()};
		System.out.println(StringUtils.join(cmd, ' '));
        exec(cmd);
	}

	/** 
	 * commit all cached changes
	 * @param file the file
	 * @param comment checkin comment
	 */
	public void svnCommit(String comment) throws IOException {
		if (uncommittedChanges.size() == 0) {
			return;
		}
		List<String> fullCommand = new ArrayList<String>();
		fullCommand.add("svn");
		fullCommand.add("commit");
		fullCommand.add("-m");
		fullCommand.add(comment);
		fullCommand.addAll(uncommittedChanges);
		String[] cmd = fullCommand.toArray(
				new String[fullCommand.size()]);

		System.out.println(StringUtils.join(cmd, ' '));
		exec(cmd);
        uncommittedChanges.clear();
	}

}
