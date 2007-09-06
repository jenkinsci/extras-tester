package hudson.model;

import hudson.tasks.Builder;
import hudson.tasks.Shell;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.servlet.ServletContext;

import junit.framework.TestCase;

import org.easymock.EasyMock;

public class FreeStyleProjectTest extends TestCase {
    private int numbuilds = 0;

    public void testCreateProject() throws Exception {
        // Create a new temporary directory
        File dir = File.createTempFile("hudson", null);
        dir.delete();
        dir.mkdir();

        // FIXME does Hudson really need a ServletContext?
        Hudson hudson = new Hudson(dir, EasyMock.createMock(ServletContext.class));

        // Limit to 1 executor
        // FIXME would be nice to have a Hudson.setNumExecutors() method
        Field numExecutors = Hudson.class.getDeclaredField("numExecutors");
        numExecutors.setAccessible(true);
        numExecutors.set(hudson, 1);
        Method updateComputerList = Hudson.class.getDeclaredMethod("updateComputerList", new Class[] {});
        updateComputerList.setAccessible(true);
        updateComputerList.invoke(hudson, new Object[] {});

        FreeStyleProject project = new FreeStyleProject(Hudson.getInstance(), "test");
        setCommand(project, "echo Hello World");
        Result result;
        result = build(project);
        assertTrue(result.equals(Result.SUCCESS));
        setCommand(project, "nonexistentcommand");
        result = build(project);
        assertTrue(result.equals(Result.FAILURE));
    }

    private void setCommand(FreeStyleProject project, String command) throws Exception {
        // FIXME would be nice to be able to set the builders programmatically
        Field buildersField = Project.class.getDeclaredField("builders");
        buildersField.setAccessible(true);
        List<Builder> builders = ((List<Builder>) buildersField.get(project));
        builders.clear();
        builders.add(new Shell(command));
    }

    private Result build(Project project) throws Exception {
        numbuilds++;
        project.scheduleBuild();
        while (project.getBuilds().size() != numbuilds || project.getBuildByNumber(numbuilds).isBuilding()) {
            Thread.sleep(100);
        }
        return ((Build) project.getBuildByNumber(numbuilds)).getResult();
    }
}
