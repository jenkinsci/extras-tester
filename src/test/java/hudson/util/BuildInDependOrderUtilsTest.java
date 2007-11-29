package hudson.util;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.HudsonTestCase;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Publisher;
import hudson.util.BuildInDependOrderTestUtils.EventType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.AssertionFailedError;

public class BuildInDependOrderUtilsTest extends HudsonTestCase {

	private BuildInDependOrderTestUtils utils;
	private FreeStyleProject proj1;
	private FreeStyleProject proj2;
	private List<String> buildLog;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		utils = new BuildInDependOrderTestUtils();
		proj1 = (FreeStyleProject) hudson.createProject(
				FreeStyleProject.DESCRIPTOR, "1");
		proj2 = (FreeStyleProject) hudson.createProject(
				FreeStyleProject.DESCRIPTOR, "2");
		hudson.rebuildDependencyGraph();
		buildLog = new ArrayList<String>();
	}

	public void testRelatedAs() throws IOException {
		assertEquals("unrelated", BuildInDependOrderTestUtils.UNRELATED,
				utils.relatedAs(proj1, proj2));
		proj1.addPublisher(new BuildTrigger(Arrays
				.asList(new AbstractProject[] { proj2 }), null));
		hudson.rebuildDependencyGraph();
		assertEquals("dependee",
				BuildInDependOrderTestUtils.THIS_IS_DEPENDEE, utils
						.relatedAs(proj1, proj2));
		Publisher firstPublisher = proj1.getPublishers().values().iterator()
				.next();
		proj1.removePublisher(firstPublisher.getDescriptor());
		proj2.addPublisher(new BuildTrigger(Arrays
				.asList(new AbstractProject[] { proj1 }), null));
		hudson.rebuildDependencyGraph();
		assertEquals("dependent",
				BuildInDependOrderTestUtils.THIS_IS_DEPENDENT, utils
						.relatedAs(proj1, proj2));
	}
	
	public void testIndexOf() {
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.FINISHED);
		assertEquals(-1, utils.indexOf(proj1, EventType.FINISH, buildLog));
		assertEquals( 0, utils.indexOf(proj1, EventType.START, buildLog));
		assertEquals( 1, utils.indexOf(proj2, EventType.FINISH, buildLog));
		assertEquals(-1, utils.indexOf(proj2, EventType.START, buildLog));
	}
	
	public void testSecondDidNotStartUntilFirstDone() { 
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.STARTED);
		try { 
			utils.assertSecondDidNotStartBeforeFirstFinished(proj1, proj2, buildLog);
			fail();
		} catch (AssertionFailedError afe) {}
		buildLog.clear();
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.FINISHED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.STARTED);
	}

	public void testBothStartedBeforeEitherFinished() { 
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.FINISHED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.FINISHED);
		utils.assertBothStartedBeforeEitherFinished(proj1, proj2, buildLog);
		buildLog.clear();
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.FINISHED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.STARTED);
		try { 
			utils.assertSecondDidNotStartBeforeFirstFinished(proj1, proj2, buildLog);
			fail();
		} catch (AssertionFailedError afe) {}
	}

	public void testThisStartedBeforeAnyFinished() { 
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.FINISHED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.FINISHED);
		utils.assertThisProjectStartedBeforeAnyOtherFinished(proj1, buildLog);
		buildLog.clear();
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj2.getName() + BuildInDependOrderTestUtils.FINISHED);
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.STARTED);
		buildLog.add(proj1.getName() + BuildInDependOrderTestUtils.FINISHED);
		try { 
			utils.assertThisProjectStartedBeforeAnyOtherFinished(proj1, buildLog);
			fail();
		} catch (AssertionFailedError afe) {}
	}

}
