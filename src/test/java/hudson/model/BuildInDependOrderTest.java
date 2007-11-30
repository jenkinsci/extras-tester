package hudson.model;

import hudson.tasks.BuildTrigger;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;
import hudson.util.BuildInDependOrderTestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;

import antlr.ANTLRException;

/**
 * Test case for Commit spanning multiple interdependent projects.
 * 
 * Project dependencies are: 
 * 1 <- 2 <- a 
 * 2 <- b 
 * c (has no dependencies)
 * 
 * 
 * 
  
#Hudson logging related to dependency based build order
java.util.logging.ConsoleHandler.level = FINER
hudson.DependencyRunner.level = FINE 
hudson.model.Executor.level = FINE
hudson.triggers.Trigger.level = FINE 

 * 
 * @author Brian Westrich, bw@mcwest.com
 */
public class BuildInDependOrderTest extends SubversionTestCase {
	
	private static final String BUILD_SHELL = "build.sh";
	private static String BUILD_LOG_NAME = "build.log";

	/**
	 * class containing all project artifacts needed for testing.
	 */
	public static class TestProjectBuildOrder {

		SubversionTestCase testCase; 
		public final FreeStyleProject project;
		public final File wc; // project base dir
		private File sampleWsFile; // sample file

		public TestProjectBuildOrder(SubversionTestCase tc, FreeStyleProject project, File workingDir) {
			this.testCase = tc;
			this.project = project;
			this.wc = workingDir;
			try {
				createBuildShell();
				this.sampleWsFile = createSampleWorkspaceFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private File createBuildShell() throws IOException {
			File file = new File(wc, BUILD_SHELL);
			OutputStream out = new FileOutputStream(file);
			List<String> lines = new ArrayList<String>();
			lines.add("echo $1" + BuildInDependOrderTestUtils.STARTED 
					+ ">> ../../build.log");
			lines.add("sleep 5");
			lines.add("echo $1" + BuildInDependOrderTestUtils.FINISHED 
					+ ">> ../../build.log");
			lines.add("exit 0");
			IOUtils.writeLines(lines, null, out);
			out.close();
			testCase.svnAdd(file);
			return file;
		}

		private File createSampleWorkspaceFile() throws IOException {
			File file = new File(wc, "sample.txt");
			OutputStream out = new FileOutputStream(file);
			IOUtils.write("test content", out);
			out.close();
			testCase.svnAdd(file);
			return file;
		}

		/**
		 * start all triggers for the project. included for completeness, but
		 * not used since slower than Trigger.checkTriggers().
		 */
		@SuppressWarnings("unchecked")
		public void startTriggers() {
			for (Trigger t : project.getTriggers().values()) {
				t.start(project, false);
			}
		}

		/**
		 * cause the project to be rebuilt the next time subversion polling
		 * happens. Note: no svn commit occurs. 
		 * 
		 * @throws IOException
		 */
		public void touch() throws IOException {
			OutputStream out = new FileOutputStream(sampleWsFile);
			IOUtils.write(new Date().toString(), out);
			out.close();
			testCase.svnCache(sampleWsFile);
		}

		@Override
		public String toString() {
			return project.getName();
		}
		
	}

	TestProjectBuildOrder proj1, proj2, projA, projB, projC;
	List<TestProjectBuildOrder> allProjects = new ArrayList<TestProjectBuildOrder>();

	private void createProjects() throws ANTLRException, IOException {
		proj1 = createSubversionProject("1");
		proj2 = createSubversionProject("2");
		projA = createSubversionProject("A");
		projB = createSubversionProject("B");
		projC = createSubversionProject("C");
		svnCommit("create projects");

		// Setup dependencies
		proj1.project.addPublisher(new BuildTrigger(Arrays
				.asList(new AbstractProject[] { proj2.project }), null));
		proj2.project.addPublisher(new BuildTrigger(Arrays
				.asList(new AbstractProject[] { projA.project, projB.project }),
				null));
		hudson.rebuildDependencyGraph();

		setCommandForAllProjects("sh -xe " + BUILD_SHELL + " $JOB_NAME");
		startPollingForAllProjects();

	}

	private void startPollingForAllProjects() {
		for (TestProjectBuildOrder project : allProjects) {
			project.startTriggers();
		}
	}

	private void setCommandForAllProjects(String cmd) {
		for (TestProjectBuildOrder project : allProjects) {
			setCommand(project.project, cmd);
		}
	}

	private void touchAllProjects() throws IOException {
		for (TestProjectBuildOrder project : allProjects) {
			project.touch();
		}
		assertEquals(allProjects.size(), uncommittedChanges.size());
		svnCommit("touched all projects");
	}

	private final int WAIT_FOR_IN_SECONDS = 80; 
	private void waitForAllProjectsToBuild(int buildNumber) {
		for (TestProjectBuildOrder project : allProjects) {
			waitForBuild(buildNumber, project.project, WAIT_FOR_IN_SECONDS);
		}
	}

	private TestProjectBuildOrder createSubversionProject(String name)
			throws ANTLRException, IOException {
		FreeStyleProject project = (FreeStyleProject) hudson.createProject(
				FreeStyleProject.DESCRIPTOR, name);
		File workingDir = createNonpollingSubversionProject(project);
		TestProjectBuildOrder tfsProject = new TestProjectBuildOrder(
				this, project, workingDir);
		allProjects.add(tfsProject);
		return tfsProject;
	}

	/**
	 * build the top level projects for the first time.
	 * 
	 * @throws ANTLRException
	 * @throws IOException
	 */
	protected void buildTopLevelProjects() throws ANTLRException, IOException {
		createProjects();
		assertEquals(0, proj1.project.getBuilds().size());
		assertSuccess(build(proj1.project));
		assertSuccess(build(projC.project));
		waitForAllProjectsToBuild(1);
		clearBuildLog();
	}

	private void clearBuildLog() throws IOException {
		File file = getBuildLogFile();
		file.delete();
		assertEquals(0, readBuildLog().size());
	}

	private File getBuildLogFile() throws IOException {
		File file = new File(proj1.project.getParent().root, "jobs/"
				+ BUILD_LOG_NAME);
		if (!file.exists()) {
			file.createNewFile();
		}
		return file;
	}

	
	@SuppressWarnings("unchecked")
	private List<String> readBuildLog() throws IOException {
		File file = getBuildLogFile();
		FileInputStream in = new FileInputStream(file);
		List<String> list = IOUtils.readLines(new FileInputStream(file));
		in.close();
		return list;
	}

	private void doScmTriggeredBuild() throws ANTLRException, IOException {
		int BUILD_NUMBER_TO_WAIT_FOR = 2;
		buildTopLevelProjects();
		touchAllProjects();
		startPollingForAllProjects();
		waitForAllProjectsToBuild(BUILD_NUMBER_TO_WAIT_FOR);
	}

	private void doTestOrderMatchesExpected(String[] expectedOrderStringArray)
	throws IOException {
		List<String> expectedOrder = Arrays.asList(expectedOrderStringArray);
		List<String> actualOrder = readBuildLog();
		assertEquals(expectedOrder, actualOrder);
	}

	private void doTestOrderMatchesDependencies()
	throws IOException {
		List<String> actualOrder = readBuildLog();
		new BuildInDependOrderTestUtils().assertOrderIsCorrect(actualOrder,
				allProjects);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		// explicitly set statics to Hudson default values,
		// since JUnit does not reset these after each test
		// method is run.
		setNumExecutors(2);
		SCMTrigger.DESCRIPTOR.synchronousPolling = false;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(0);
		//Executor.POSTPONE_BUILDS_IF_DEPENDEE_BUILDING = false; 
	}

	/**
	 * make sure can create/build projects.
	 */
	public void DONE_testBuildProjects() throws ANTLRException, IOException {
		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
		buildTopLevelProjects();
	}

// the following tests are commented out since they haven't been run 
//  yet on all supported Hudson environments. 	
//	public void testCurrentHudsonBehavior() throws Exception {
//
//		setNumExecutors(5);
//		SCMTrigger.DESCRIPTOR.synchronousPolling = false;
//		SCMTrigger.DESCRIPTOR.setPollingThreadCount(0);
//		doScmTriggeredBuild();
//
//		// note that B and A start before 2 does,
//		// even though they depend on 2. 
//		String observedOrder[] = { 
//				"1s", "Bs", "As", "2s", "Cs", 
//				"Bf", "Af", "1f", "2f", "Cf"
//		};
//		
//		doTestOrderMatchesExpected(observedOrder);
//		// this next line fails because 2 starts before 1 is finished
//		//doTestOrderMatchesDependencies();
//	}
//
//	/**
//	 * Test synchronous SCM polling with serial builds.
//	 * 
//	 * @throws Exception
//	 */
//	public void testSynchronousPollingAndSerialBuilds() throws Exception {
//
//		setNumExecutors(1);
//		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
//		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
//		doScmTriggeredBuild();
//
//		// While the build order respects dependencies, lack of parallel builds 
//		// prevents C from starting until other (unrelated) projects are finished.
//		// Ideally, C would start at the same time as 1. 
//		String observedOrder[] = { 
//				"1s", "1f", 
//				"2s", "2f", 
//				"Bs", "Bf", 
//				"As", "Af", 
//				"Cs", "Cf" 
//		};
//		
//		doTestOrderMatchesExpected(observedOrder);
//		// this next line fails because C starts too late 
//		//doTestOrderMatchesDependencies();
//	}
//
//	/**
//	 * Test synchronous SCM polling with parallel builds.
//	 * 
//	 * @throws Exception
//	 */
//	public void testSynchronousPollingAndParallelBuilds() throws Exception {
//
//		setNumExecutors(5);
//		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
//		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
//		doScmTriggeredBuild();
//
//		// When we enable parallel builds, dependency order is no longer 
//		// respected (even though we have serial SCM polling!).
//		String observedOrder[] = { 
//				"2s", "1s", "Cs", "As", "Bs", 
//				"2f", "Af", "1f", "Cf", "Bf" 
//		};
//		
//		doTestOrderMatchesExpected(observedOrder);
//		// this next line fails because 2 starts before 1
//		//doTestOrderMatchesDependencies();
//	}
//
//	/**
//	 * Test synchronous SCM polling with parallel builds using modified Executor functionality.
//	 * 
//	 * @throws Exception
//	 */
//	public void testSynchronousPollingAndParallelBuildsWithConditionalExecution() throws Exception {
//
//		setNumExecutors(5);
//		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
//		SCMTrigger.DESCRIPTOR.setPollingThreadCount(0);
//		//Executor.POSTPONE_BUILDS_IF_DEPENDEE_BUILDING = true; 
//		doScmTriggeredBuild();
//
//		// Looks good.
//		String observedOrder[] = { 
//				"1s", "Cs", "1f", "Cf", 
//				"2s", "2f", 
//				"Bs", "As", "Bf", "Af" 
//		};
//		
//		doTestOrderMatchesExpected(observedOrder);
//		doTestOrderMatchesDependencies();
//	}
//	
//	/**
//	 * Test using current Hudson behavior (asynch polling) with one polling thread.
//	 * 
//	 * @throws Exception
//	 */
//	public void testCurrentHudsonWithOneExecutorAndOnePollingThread() throws Exception {
//
//		setNumExecutors(1);
//		SCMTrigger.DESCRIPTOR.synchronousPolling = false;
//		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
//		doScmTriggeredBuild();
//
//		// While the build order respects dependencies, lack of parallel builds 
//		// prevents C from starting until other (unrelated) projects are finished.
//		// Ideally, C would start at the same time as 1. 
//		String observedOrder[] = { 
//				"1s", "1f", 
//				"2s", "2f", 
//				"Bs", "Bf", 
//				"As", "Af", 
//				"Cs", "Cf" 
//		};
//		
//		doTestOrderMatchesExpected(observedOrder);
//		// this next line fails because C starts too late 
//		//doTestOrderMatchesDependencies();
//	}
	
}
