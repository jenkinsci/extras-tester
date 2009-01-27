package hudson.model;

import hudson.plugins.git.GitPublisher;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.RemoteRepository;
import hudson.scm.ChangeLogSet;
import hudson.tasks.Mailer;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.util.IOUtil;

public class GitSCMTest extends HudsonTestCase {
	File externalRepo;
	File anotherRepo;

	@Override
	/**
	 * Initializes the Git repository. Provides
	 * <tt>File externalRepo</tt> to the test case.
	 */
	protected void setUp() throws Exception {
		super.setUp();
		externalRepo = createTempDir("hudson-gittest");
		exec(externalRepo, "git", "init");
		exec(externalRepo, "git", "config", "--add", "user.name", "John Doe");
		exec(externalRepo, "git", "config", "--add", "user.email", "john@doe.com");
		createTestFile(externalRepo, "test");
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 1");
		exec(externalRepo, "git", "rm", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 2");

		anotherRepo = createTempDir("hudson-gittest");
		exec(anotherRepo, "git", "init");
		exec(anotherRepo, "git", "config", "--add", "user.name", "John Doe");
		exec(anotherRepo, "git", "config", "--add", "user.email", "john@doe.com");
		createTestFile(anotherRepo, "help");
		exec(anotherRepo, "git", "add", "help");
		exec(anotherRepo, "git", "commit", "-m", "Commit");
	}

	protected void createTestFile(File repo, String name) throws Exception {
		File testFile = new File(repo, name);
		FileOutputStream out = new FileOutputStream(testFile);
		IOUtil.copy("Hello, World!", out);
		out.close();
	}
	
	public void testPushTags() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), null, false, false, null, new ArrayList<RemoteRepository>(), null, null));
        p.addPublisher(new GitPublisher());
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);
        // FIXME wait for the publisher to tag origin repo
        Thread.sleep(1000);
        assertEquals("hudson-test-1-SUCCESS\n", exec(externalRepo, "git", "tag", "-l"));
	}

	/**
	 * FIXME clone is not performed
	 * 
	 * Test GitSCM with a nested remote repository
	 * 
	 * @see https://hudson.dev.java.net/issues/show_bug.cgi?id=2782
	 * @throws Exception
	 */
	public void bugTestGitSCMNestedRepository() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();
        repositories.add(new RemoteRepository("another", anotherRepo.toString(), null));
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), null, false, false, null, repositories, null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);
        assertNotNull(p.getWorkspace());
        assertTrue("Directory 'another' does not exist", p.getWorkspace().child("another").exists());
        assertTrue("File 'another/help' does not exist", p.getWorkspace().child("another").child("help").exists());
	}

	/**
	 * Test GitSCM with no specific branch, ie GitSCM will build all "tip" branches
	 * @throws Exception
	 */
	public void testGitSCMAllBranches() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), null, false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);

		createTestFile(externalRepo, "test");
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 3");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        assertEquals(1, changes.getItems().length);
        ChangeLogSet.Entry lastChange = (ChangeLogSet.Entry)changes.getItems()[0];
        assertEquals("Commit 3", lastChange.getMsg().trim());
        assertEquals("John Doe", lastChange.getAuthor().getId().trim());
        Mailer.UserProperty user = lastChange.getAuthor().getProperty(Mailer.UserProperty.class);
        // See https://hudson.dev.java.net/issues/show_bug.cgi?id=2827
        // Mail is not being sent to people who broke the build when using Git
        //assertEquals("john@doe.com", user.getAddress());

		exec(externalRepo, "git", "branch", "newbranch");
		createTestFile(externalRepo, "test2");
		exec(externalRepo, "git", "add", "test2");
		exec(externalRepo, "git", "commit", "-m", "Commit in newbranch");

		assertTrue(p.pollSCMChanges(new StreamTaskListener(System.out)));

		b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        assertEquals(1, changes.getItems().length);
        lastChange = (ChangeLogSet.Entry)changes.getItems()[0];
        assertEquals("Commit in newbranch", lastChange.getMsg().trim());
        
        assertFalse("Not expecting changes at this point", p.pollSCMChanges(new StreamTaskListener(System.out)));
	}

	/**
	 * Test GitSCM with a specific branch
	 * @throws Exception
	 */
	public void testGitSCMValidBranch() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), "origin/newbranch", false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertFailure(r);

		exec(externalRepo, "git", "checkout", "-b", "newbranch");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        ChangeLogSet changes = b.getChangeSet();
        // First-time build does not create change sets
        assertEquals(0, changes.getItems().length);

		createTestFile(externalRepo, "test2");
		exec(externalRepo, "git", "add", "test2");
		exec(externalRepo, "git", "commit", "-m", "Commit in newbranch");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        // We built newbranch
        assertEquals(1, changes.getItems().length);
        ChangeLogSet.Entry lastChange = (ChangeLogSet.Entry)changes.getItems()[0];
        assertEquals("Commit in newbranch", lastChange.getMsg().trim());

		exec(externalRepo, "git", "checkout", "master");
		createTestFile(externalRepo, "test");
		exec(externalRepo, "git", "add", "test");
		exec(externalRepo, "git", "commit", "-m", "Commit 3");
        b = build(p);
        r = b.getResult();
        assertSuccess(r);
        changes = b.getChangeSet();
        // We didn't build master
        assertEquals(0, changes.getItems().length);
	}

	/**
	 * Test with a branch setting that cannot be resolved as a valid branch
	 * name, then test with an existing branch missing the leading origin/
	 * 
	 * @throws Exception
	 */
	public void testGitSCMBadBranch() throws Exception {
        Project p = new FreeStyleProject(Hudson.getInstance(), "test");
        p.setScm(new GitSCM(externalRepo.getAbsolutePath(), "newbranch", false, false, null, new ArrayList<RemoteRepository>(), null, null));
        setCommand(p, "echo Hello");
        Build b = build(p);
        Result r = b.getResult();
        assertFailure(r);

		exec(externalRepo, "git", "checkout", "-b", "newbranch");
        b = build(p);
        r = b.getResult();
        // GitSCM will prepend branch name with origin/
        assertSuccess(r);
	}
}
