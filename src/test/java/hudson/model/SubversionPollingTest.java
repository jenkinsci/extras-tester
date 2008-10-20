package hudson.model;

import hudson.DependencyRunner;
import hudson.DependencyRunner.ProjectRunnable;
import hudson.tasks.BuildTrigger;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

/**
 * Test case for Commit spanning multiple interdependent projects
 * See http://www.nabble.com/Commit-spanning-multiple-interdependant-projects-tf3870814.html#a10966678
 */
public class SubversionPollingTest extends SubversionTestCase {
    private static final Logger LOGGER = Logger.getLogger(SubversionPollingTest.class.getName());

    FreeStyleProject projectA, projectB, projectC;
    File wcProjectA, wcProjectB;

    @SuppressWarnings("unchecked")
    protected void createProjects() throws Exception {
        projectA = (FreeStyleProject) hudson.createProject(FreeStyleProject.DESCRIPTOR, "projectFoo");
        projectB = (FreeStyleProject) hudson.createProject(FreeStyleProject.DESCRIPTOR, "projectBar");
        projectC = (FreeStyleProject) hudson.createProject(FreeStyleProject.DESCRIPTOR, "projectAbc");
        
        // Setup dependency between projects A and B and between A and C
        projectA.addPublisher(new BuildTrigger(Arrays.asList(new AbstractProject[]{projectB, projectC}), null));
        // And between B and C
        projectB.addPublisher(new BuildTrigger(Arrays.asList(new AbstractProject[]{projectC}), null));
        hudson.rebuildDependencyGraph();

        wcProjectA = createPollingSubversionProject(projectA);
        wcProjectB = createPollingSubversionProject(projectB);
        
        setCommand(projectB, "sh -xe build.sh");
    }

    protected void buildProjects() throws Exception {
        createProjects();

        File build = new File(wcProjectB, "build.sh");
        OutputStream out = new FileOutputStream(build);
        IOUtils.write("exit 0", out);
        out.close();
        svnAdd(build);
        svnCommit("build");

        assertSuccess(build(projectA).getResult());
        assertEquals(projectA.getBuilds().size(), 1);
        // projectB #1 is already in the queue because of the dependency
        //assertNull(build(projectB));
        assertSuccess(waitForNextBuild(projectB).getResult());
        assertEquals(projectB.getBuilds().size(), 1);

        File hello = new File(wcProjectA, "hello");
        exec("touch", hello.getPath());
        exec("svn", "add", hello.getPath());
        exec("svn", "commit", "-m", "hello", hello.getPath());
        out = new FileOutputStream(build);
        IOUtils.write("test -e ../../projectFoo/workspace/hello\n", out);
        out.close();
        exec("svn", "commit", "-m", "build", build.getPath());
    }

    int count = 0;
    public void testDeps() throws Exception {
        createProjects();
        new DependencyRunner(new ProjectRunnable() {
            public void run(AbstractProject p) {
                if (count == 0)
                    assertEquals(projectA, p);
                else if (count == 1)
                    assertEquals(projectB, p);
                else if (count == 2)
                    assertEquals(projectC, p);
                count++;
            }
        }).run();
        assertEquals(3, count);
    }

    public void testSubversionPolling() throws Exception {
        buildProjects();

        // poll for changes manually, this is a copy/paste of Trigger.Cron.run()
        for (AbstractProject<?,?> p : hudson.getAllItems(AbstractProject.class)) {
            for (Trigger t : p.getTriggers().values()) {
                t.run();
                // Introduce a delay to make polling nearly-synchronous
                Thread.sleep(1000);
            }
        }

        waitForBuild(2, projectA);
        waitForBuild(3, projectB);

        assertSuccess(projectB.getBuildByNumber(1).getResult());
        // projectBar has built before projectFoo
        assertFailure(projectB.getBuildByNumber(2).getResult());
        assertSuccess(projectB.getBuildByNumber(3).getResult());

        assertSuccess(projectA.getBuildByNumber(1).getResult());
        assertSuccess(projectA.getBuildByNumber(2).getResult());
    }

    public void testSynchronousSubversionPolling() throws Exception {
        SCMTrigger.DESCRIPTOR.synchronousPolling = true;
        SCMTrigger.DESCRIPTOR.setPollingThreadCount(1);
        buildProjects();
        
        Trigger.checkTriggers(new GregorianCalendar());
        Trigger.checkTriggers(new GregorianCalendar());
        Trigger.checkTriggers(new GregorianCalendar());

        waitForBuild(2, projectA);
        waitForBuild(2, projectB);

        assertSuccess(projectB.getBuildByNumber(1).getResult());
        assertSuccess(projectB.getBuildByNumber(2).getResult());

        assertSuccess(projectA.getBuildByNumber(1).getResult());
        assertSuccess(projectA.getBuildByNumber(2).getResult());
    }
}
