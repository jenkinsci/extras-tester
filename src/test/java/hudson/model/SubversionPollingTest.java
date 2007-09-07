package hudson.model;

import hudson.tasks.BuildTrigger;
import hudson.triggers.SCMTrigger;
import hudson.triggers.Trigger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

/**
 * Test case for Commit spanning multiple interdependant projects
 * See http://www.nabble.com/Commit-spanning-multiple-interdependant-projects-tf3870814.html#a10966678
 */
public class SubversionPollingTest extends SubversionTestCase {
    private static final Logger LOGGER = Logger.getLogger(SubversionPollingTest.class.getName());

    private static final String EVERY_SECOND = "* * * * *";

    @SuppressWarnings("unchecked")
    public void testSubversionPolling() throws Exception {
        FreeStyleProject projectA = (FreeStyleProject) hudson.createProject(FreeStyleProject.DESCRIPTOR, "projectFoo");
        Trigger t1 = new SCMTrigger(EVERY_SECOND);
        projectA.addTrigger(t1);
        t1.start(projectA, false);
        File wcProjectA = createSubversionProject(projectA);
        FreeStyleProject projectB = (FreeStyleProject) hudson.createProject(FreeStyleProject.DESCRIPTOR, "projectBar");
        
        // Setup dependency between projects A and B
        projectA.addPublisher(new BuildTrigger(Arrays.asList(new AbstractProject[]{projectB}), null));
        hudson.rebuildDependencyGraph();

        Trigger t2 = new SCMTrigger(EVERY_SECOND);
        projectB.addTrigger(t2);
        t2.start(projectB, false);
        File wcProjectB = createSubversionProject(projectB);
        setCommand(projectB, "sh -xe build.sh");
        File build = new File(wcProjectB, "build.sh");
        OutputStream out = new FileOutputStream(build);
        IOUtils.write("exit 0", out);
        out.close();
        exec("svn", "add", build.getPath());
        exec("svn", "commit", "-m", "build", build.getPath());

        assertSuccess(build(projectA));
        assertEquals(projectA.getBuilds().size(), 1);
        // projectB #1 is already in the queue because of the dependency
        //assertNull(build(projectB));
        assertSuccess(waitForNextBuild(projectB));
        assertEquals(projectB.getBuilds().size(), 1);

        File hello = new File(wcProjectA, "hello");
        exec("touch", hello.getPath());
        exec("svn", "add", hello.getPath());
        exec("svn", "commit", "-m", "hello", hello.getPath());
        out = new FileOutputStream(build);
        IOUtils.write("test -e ../../projectFoo/workspace/hello\n", out);
        out.close();
        exec("svn", "commit", "-m", "build", build.getPath());

        // poll for changes manually
        Hudson inst = Hudson.getInstance();
        for (AbstractProject<?,?> p : inst.getAllItems(AbstractProject.class)) {
            for (Trigger t : p.getTriggers().values()) {
                LOGGER.fine("cron checking "+p.getName());
                // Introduce a delay to make polling nearly-synchronous
                Thread.sleep(1000);
                // FIXME could we make Trigger.run() public?
                Method run = Trigger.class.getDeclaredMethod("run", new Class[0]);
                run.setAccessible(true);
                run.invoke(t, new Object[0]);
            }
        }

        waitForBuild(2, projectA);
        waitForBuild(3, projectB);

        assertSuccess(projectB.getBuildByNumber(1).getResult());
        assertFailure(projectB.getBuildByNumber(2).getResult());
        assertSuccess(projectB.getBuildByNumber(3).getResult());

        assertSuccess(projectA.getBuildByNumber(1).getResult());
        assertSuccess(projectA.getBuildByNumber(2).getResult());
    }
}
