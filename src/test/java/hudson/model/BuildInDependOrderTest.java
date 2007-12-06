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
  
#Hudson logging related to dependency based build order.
#Make this change to the existing entry in the 
#JRE_HOME/lib/logging.properties file
java.util.logging.ConsoleHandler.level = FINER
#Add these entries to the 
#JRE_HOME/lib/logging.properties file
hudson.DependencyRunner.level = FINE 
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
		// purposely create them in a different order than we want them to build in, to 
		// make sure successful order isn't just occuring because we happened to create 
		// the projects in that same way. 
		proj2 = createSubversionProject("2");
		proj1 = createSubversionProject("1");
		projB = createSubversionProject("B");
		projA = createSubversionProject("A");
		projC = createSubversionProject("C");
		svnCommit("create projects");
		setCommandForAllProjects("sh -xe " + BUILD_SHELL + " $JOB_NAME");
	}

	private void setupDependencies() throws IOException {
		// Setup dependencies
		for (TestProjectBuildOrder project : allProjects) { 
			assertEquals(0, project.project.getUpstreamProjects().size());
			assertEquals(0, project.project.getDownstreamProjects().size());
		}
		proj1.project.addPublisher(new BuildTrigger(Arrays
				.asList(new AbstractProject[] { proj2.project }), null));
		proj2.project.addPublisher(new BuildTrigger(Arrays
				.asList(new AbstractProject[] { projA.project, projB.project }),
				null));
		hudson.rebuildDependencyGraph();
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

	private final int WAIT_FOR_IN_SECONDS = 120; 
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

	private void doInitialBuildOfAllProjects() throws ANTLRException, IOException {
		createProjects();
		
		// have to start polling before we build, 
		// or will get error related to not being 
		// able to find scm polling log file. 
		startPollingForAllProjects();
		
		//buildAllProjects();
		waitForAllProjectsToBuild(1);
		for (TestProjectBuildOrder project : allProjects) {
			assertEquals(1, project.project.builds.size());
		}
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
		doInitialBuildOfAllProjects();
		setupDependencies();
		touchAllProjects();
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
		//This next change was emailed to the dev list as a patch, but 
		// has not been implemented. 
		//Executor.POSTPONE_BUILDS_IF_DEPENDEE_BUILDING = false; 
	}

	/**
	 * make sure can create/build projects.
	 */
	public void HIDEtestBuildProjects() throws ANTLRException, IOException {
		doInitialBuildOfAllProjects();
	}

	/**
	 * Test current Hudson behavior. 
	 * This test fails because the build order is random on each run. 
	 * @throws Exception
	 */
	public void FAILStestCurrentHudsonBehavior() throws Exception {

		setNumExecutors(5);
		SCMTrigger.DESCRIPTOR.synchronousPolling = false;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(0);
		doScmTriggeredBuild();

		// note that B and A start before 2 does,
		// even though they depend on 2. 
		String observedOrder[] = { 
				"1s", "Cs", "As", "2s", "Bs", 
				"1f", "Cf", "Af", "2f"
		};
		
		doTestOrderMatchesExpected(observedOrder);
		// this next line fails because 2 starts before 1 is finished
		//doTestOrderMatchesDependencies();
	}

	/**
	 * Test synchronous SCM polling with serial builds.
	 * This test fails because the build order is random on each run.
	 * NOTE: These settings are the only ones that fully respect dependency order.  
	 * @throws Exception
	 */
	public void FAILStestSynchronousPollingAndSerialBuilds() throws Exception {

		setNumExecutors(1);
		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
		doScmTriggeredBuild();

		// While the build order respects dependencies, lack of parallel builds 
		// prevents C from starting until other (unrelated) projects are finished.
		// Ideally, C would start at the same time as 1. 
		String observedOrderSometimes[] = { 
				"1s", "1f", 
				"2s", "2f", 
				"Bs", "Bf", 
				"As", "Af", 
				"Cs", "Cf" 
		};
		String observedOrder[] = { 
				"Cs", "Cf", 
				"1s", "1f", 
				"2s", "2f", 
				"Bs", "Bf", 
				"As", "Af" 
		};
		
		doTestOrderMatchesExpected(observedOrder);
		// this next line fails because C starts too late 
		//doTestOrderMatchesDependencies();
	}

	/**
	 * Test synchronous SCM polling with parallel builds.
	 * This test fails because the build order is random on each run. 
	 * @throws Exception
	 */
	public void FAILStestSynchronousPollingAndParallelBuilds() throws Exception {

		setNumExecutors(5);
		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
		doScmTriggeredBuild();

		// When we enable parallel builds, dependency order is no longer 
		// respected (even though we have serial SCM polling!).
		String observedOrder[] = { 
				"2s", "1s", "Bs", "As", "Cs", 
				"2f", "Af", "1f", "Cf", "Bf" 
		};
		
		doTestOrderMatchesExpected(observedOrder);
		// this next line fails because 2 starts before 1
		//doTestOrderMatchesDependencies();
	}

	/**
	 * Test synchronous SCM polling with parallel builds using modified Executor functionality.
	 * This test fails because the build order is random on each run.
	 * If this technique worked, we could give users the choice of parallel builds or parallel SCM polling. 
	 * The help feature for such a feature might read: 
  "If you want interdependent projects to always build in dependency order, you must use single threaded SCM polling. 
  Use multithreaded SCM polling to get the fastest builds in situations where SCM polling is I/O intensive."
  
	 * @throws Exception
	 */
	public void FAILStestSynchronousPollingAndParallelBuildsWithConditionalExecution() throws Exception {

		setNumExecutors(5);
		SCMTrigger.DESCRIPTOR.synchronousPolling = true;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(0);
		//This next change was emailed to the dev list as a patch, but 
		// has not been implemented. 
		//Executor.POSTPONE_BUILDS_IF_DEPENDEE_BUILDING = true; 
		doScmTriggeredBuild();

//		// Looks good.
//		String observedOrder[] = { 
//				"1s", "Cs", "1f", "Cf", 
//				"2s", "2f", 
//				"Bs", "As", "Bf", "Af" 
//		};
		String observedOrder[] = { 
				"1s", "2s", "Bs", "Cs", "As", 
				"1f", "Bf", "Cf", "2f", "Af" 
		};
		
		doTestOrderMatchesExpected(observedOrder);
		doTestOrderMatchesDependencies();
	}
	
	/**
	 * Test using current Hudson behavior (asynch polling) with one executor and 
	 * one polling thread. If this works, it supports not having to implement 
	 * synchronous polling, instead we can just limit number of polling threads 
	 * and number of executors to 1. 
	 * 
	 * @throws Exception
	 */
	public void testCurrentHudsonWithOneExecutorAndOnePollingThread() throws Exception {

		setNumExecutors(1);
		SCMTrigger.DESCRIPTOR.synchronousPolling = false;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
		doScmTriggeredBuild();

		// While the build order respects dependencies, lack of parallel builds 
		// prevents C from starting until other (unrelated) projects are finished.
		// Ideally, C would start at the same time as 1. 
		String observedOrder[] = { 
				"1s", "1f", 
				"2s", "2f", 
				"As", "Af", 
				"Bs", "Bf", 
				"Cs", "Cf" 
		};
		
		doTestOrderMatchesExpected(observedOrder);
		// this next line fails because C starts too late 
		//doTestOrderMatchesDependencies();
	}
	
	/**
	 * Test using current Hudson behavior (asynch polling) with one executor and 
	 * one polling thread. If this worked, we would not have to implement 
	 * synchronous polling, instead we could just limit number of executors to 1. 
	 * 
	 * @throws Exception
	 */
	public void FAILStestCurrentHudsonWithOneExecutor() throws Exception {

		setNumExecutors(1);
		SCMTrigger.DESCRIPTOR.synchronousPolling = false;
		SCMTrigger.DESCRIPTOR.setPollingThreadCount(0);
		doScmTriggeredBuild();

		// While the build order respects dependencies, lack of parallel builds 
		// prevents C from starting until other (unrelated) projects are finished.
		// Ideally, C would start at the same time as 1. 
		String observedOrder[] = { 
				"2s", "2f", 
				"Bs", "Bf", 
				"As", "Af", 
				"Cs", "Cf", 
				"1s", "1f", 
				"2s", "2f" 
		};
		
		doTestOrderMatchesExpected(observedOrder);
		// this next line fails because C starts too late 
		//doTestOrderMatchesDependencies();
	}
	
}
