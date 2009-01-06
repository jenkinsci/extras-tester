package hudson.model;

import hudson.tasks.Builder;
import hudson.tasks.Shell;
import hudson.triggers.Trigger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletContext;

import junit.framework.TestCase;

import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;
import org.easymock.EasyMock;

public abstract class HudsonTestCase extends TestCase {
    protected static Hudson hudson;

    @Override
    protected void setUp() throws Exception {
        hudson = newHudson();

        // Limit to 1 executor
        setNumExecutors(1);
        
        // start the Hudson cron thread
        Trigger.init();
    }

    @Override
    protected void tearDown() throws Exception {
        // FIXME would be nice to be able to reset the Hudson instance programmatically
        Field theInstance = Hudson.class.getDeclaredField("theInstance");
        theInstance.setAccessible(true);
        theInstance.set(hudson, null);
    }

    protected void setNumExecutors(int i) {
        // FIXME would be nice to have a Hudson.setNumExecutors() method
        Field numExecutors;
        try {
            numExecutors = Hudson.class.getDeclaredField("numExecutors");
            numExecutors.setAccessible(true);
            numExecutors.set(hudson, i);
            Method updateComputerList = Hudson.class.getDeclaredMethod("updateComputerList", new Class[] {});
            updateComputerList.setAccessible(true);
            updateComputerList.invoke(hudson, new Object[] {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Hudson newHudson() {
        // FIXME does Hudson really need a ServletContext?
        try {
            return new Hudson(createTempDir("hudson"), EasyMock.createMock(ServletContext.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new temporary directory
     */
    protected File createTempDir(String prefix) {
        File dir;
        try {
            dir = File.createTempFile(prefix, null);
            dir.delete();
            dir.mkdir();
            return dir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Schedules a build and waits for completion
     * 
     * @param project
     *            the project to build
     * @return build result, or null if project is disabled
     * @throws Exception
     */
    protected Build build(Project project) {
        if (!project.scheduleBuild())
            return null;
        return waitForNextBuild(project);
    }

    protected Build waitForBuild(int buildToWaitFor, Project project) {
    	return waitForBuild(buildToWaitFor, project, 20);
    }

    protected Build waitForBuild(int buildToWaitFor, Project project, int timeoutInSeconds) {
    	int timeout = timeoutInSeconds * 1000;
        try {
            long slept = 0;
            while (project.getBuilds().size() != buildToWaitFor || project.getBuildByNumber(buildToWaitFor).isBuilding()) {
                Thread.sleep(100);
                slept += 100;
                if (slept >= timeout) 
                    fail("Timed out waiting " + timeoutInSeconds
                    		+ " seconds for project " + project.getName() 
                    		+ " build #" + buildToWaitFor);
            }

            Build build = ((Build) project.getBuildByNumber(buildToWaitFor));
            System.out.println(build.getLog());
            return build;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Build waitForNextBuild(Project project) {
        return waitForBuild(project.getBuilds().size() + 1, project);
    }

    /**
     * Clears the builders for the given project and add a Shell builder with specified command
     * 
     * @param project
     *            project to build
     * @param command
     *            command to run in shell
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    protected void setCommand(AbstractProject project, String command) {
        // FIXME would be nice to be able to set the builders programmatically
        Field buildersField;
        try {
            buildersField = Project.class.getDeclaredField("builders");
            buildersField.setAccessible(true);
            List<Builder> builders = ((List<Builder>) buildersField.get(project));
            builders.clear();
            builders.add(new Shell(command));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertSuccess(Result result) {
        assertTrue("Expected SUCCESS, got " + result.toString(), result.equals(Result.SUCCESS));
    }

    protected void assertFailure(Result result) {
        assertTrue("Expected FAILURE, got " + result.toString(), result.equals(Result.FAILURE));
    }

    public String exec(File wdir, String... args) throws IOException {
        Execute exec = new Execute();
        OutputStream out = new ByteArrayOutputStream();
        ExecuteStreamHandler stream = new PumpStreamHandler(out);
        exec.setStreamHandler(stream);
        if (wdir != null)
        	exec.setWorkingDirectory(wdir);
        exec.setCommandline(args);
        int status;
        try {
            status = exec.execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (status != 0)
            throw new RuntimeException("Command returned status " + status + ": " + Arrays.asList(args));
        return out.toString();
    }
    
    public void exec(String... args) throws IOException {
         exec(null, args);
    }

    /**
     * Check whether we are running on Microsoft windoze. 
     * Only tested for Windows XP. 
     * @return true if running under windows.
     * @todo research whether should instead call Hudson.isWindows() 
     */
    boolean onMsftWindows() { 
    	String osName = System.getProperty("os.name");
    	return osName.startsWith("Windows");
    }
}
