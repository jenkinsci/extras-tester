package hudson.util;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.BuildInDependOrderTest.TestProjectBuildOrder;

import java.util.List;

import junit.framework.AssertionFailedError;

/**
 * Utilities for testing that projects build in dependency order.  
 * @author Brian Westrich, bw@mcwest.com
 */
public class BuildInDependOrderTestUtils {

	// a more complete test of dependency order than merely comparing actual and expected 
	// build logs, yet less subject to the vagaries of queueing order than is the build log...	
//	public void testOrderIsCorrect() { 
//		for each project
//		  for each other project
//		     calculate whether dependee, dependent, or neither;
//		     if dependee, assert that other does not start until this finishes
//		     if dependent, assert that this does not start until other finishes 
//		     if neither, assert that both start before either one finishes
//		  if project has no dependees, 
//		    assert that it starts before any project finishes
//	}
	
	enum EventType {START, FINISH}

	// constants used in build log to denote whether the project build event is a start or a finish.
	public final static String STARTED = "s";
	public final static String FINISHED = "f";
	
	// project relationship types
	// did not use enum, as we do a switch statement with this value. 
	static final int THIS_IS_DEPENDEE = 1;
	static final int THIS_IS_DEPENDENT = 2;
	static final int UNRELATED = 3;
	
	/**
	 * assert that the build order is correct per the dependencies 
	 * @param actualOrder the order from the build log
	 * @param allProjects all projects related to the build 
	 */
	public void assertOrderIsCorrect(List<String> actualOrder, 
			List<TestProjectBuildOrder> allProjects) { 
		for (TestProjectBuildOrder thisProj : allProjects) {
			for (TestProjectBuildOrder otherProj : allProjects) {
				if (thisProj.equals(otherProj)) { 
					continue;
				}
				switch (relatedAs(thisProj.project, otherProj.project)) {
					case THIS_IS_DEPENDEE:
						assertSecondDidNotStartBeforeFirstFinished(
								thisProj.project, otherProj.project,
								actualOrder);
						break;
					case THIS_IS_DEPENDENT: 
						assertSecondDidNotStartBeforeFirstFinished(
								otherProj.project, thisProj.project, 
								actualOrder);
						break;
					case UNRELATED:
						assertBothStartedBeforeEitherFinished(thisProj.project, 
								otherProj.project, actualOrder);
						break;
				}
			}
			if (thisProj.project.getUpstreamProjects().size() == 0) {
				assertThisProjectStartedBeforeAnyOtherFinished(
						thisProj.project, actualOrder);
			}
		}
	}

	/**
	 * get relationship of project to another project
	 * package visibility for testing
	 * @param thisProject
	 * @param otherProject
	 * @return
	 */
	 int relatedAs(FreeStyleProject thisProject,
			FreeStyleProject otherProject) {
		for (AbstractProject<?,?> upstream : thisProject.getTransitiveUpstreamProjects()) { 
			if (upstream.equals(otherProject)) { 
				return THIS_IS_DEPENDENT;
			}
		}
		for (AbstractProject<?,?> downstream : thisProject.getTransitiveDownstreamProjects()) { 
			if (downstream.equals(otherProject)) { 
				return THIS_IS_DEPENDEE;
			}
		}
		return UNRELATED;
	}
	
	/** 
	 * find project log entry in build log
	 * package visibility for testing  
	 * @param project the project
	 * @param start true if start, false if finish 
	 * @param actualOrder the log
	 * @return the index of the log entry
	 */
	int indexOf(AbstractProject<?,?> project, EventType eventType, List<String> actualOrder) { 
		String label = project.getName() + (eventType==EventType.START? STARTED : FINISHED);
		int index = actualOrder.indexOf(label);
		if (index == -1) { 
			throw new AssertionFailedError("project event " + label + " not found in build log.");
		}
		return index; 
	}

	/**
	 * assert the second project did not start until first one finished. 
	 * package visibility for testing  
	 * @param dependeeProj
	 * @param dependentProj
	 * @param actualOrder
	 */
	void assertSecondDidNotStartBeforeFirstFinished(
			FreeStyleProject dependeeProj, FreeStyleProject dependentProj, 
			List<String> actualOrder) {
		int dependeeFinish = indexOf(dependeeProj, EventType.FINISH, actualOrder);
		int dependentStart = indexOf(dependentProj, EventType.START, actualOrder);
		if (dependentStart < dependeeFinish) { 
			throw new AssertionFailedError(dependentProj.getName() + " depends on " 
				+ dependeeProj.getName() + ", but it started building before "
				+ dependeeProj.getName() + " was finished building."
				);
		}
	}
	
	/**
	 * assert that both projects started before either finished.
	 * @param thisProj
	 * @param anotherProj
	 * @param actualOrder build log.
	 */
	void assertBothStartedBeforeEitherFinished(
			FreeStyleProject thisProj, FreeStyleProject anotherProj,
			List<String> actualOrder) {

		int thisStart = indexOf(thisProj, EventType.START, actualOrder);
		int otherStart = indexOf(anotherProj, EventType.START, actualOrder);
		int latestStart = thisStart > otherStart? thisStart : otherStart;
		int thisFinish = indexOf(thisProj, EventType.FINISH, actualOrder);
		int otherFinish = indexOf(anotherProj, EventType.FINISH, actualOrder);
		int earliestFinish = thisFinish < otherFinish? thisFinish : otherFinish;
		if (latestStart > earliestFinish) { 
			throw new AssertionFailedError(thisProj.getName() + " is unrelated to " 
				+ anotherProj.getName() + ", but one of these two projects did not start " +
						"until sometime after the other one was finished."
				);
		}
	}

	/**
	 * assert that that a project started before any other project finished. 
	 * @param project the project 
	 * @param actualOrder build log. 
	 */
	void assertThisProjectStartedBeforeAnyOtherFinished(
			FreeStyleProject project, List<String> actualOrder) {
		int thisStart = indexOf(project, EventType.START, actualOrder);
		for (int i = 0; i < actualOrder.size(); i++) { 
			if (i == thisStart) { 
				break;
			}
			String current = actualOrder.get(i);
			if (current.endsWith(FINISHED)) { 
				throw new AssertionFailedError("project (" + current 
						+ ") finished before top level project " 
						+ project.getName() + " started.");
			}
		}
	}
	
}
