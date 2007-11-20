package hudson.model;

import hudson.FilePath;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class Issue886Test extends SubversionTestCase {
    public void testLeak() throws Exception {
        FreeStyleProject project = new FreeStyleProject(hudson, "test");
        File projectDir = createSubversionProject(project);

        MemoryMXBean mmx = ManagementFactory.getMemoryMXBean();
        System.out.println(mmx.getHeapMemoryUsage());

        for (int i = 0; i < 1000; i++) {
            // build() is too slow, use checkout directly
            //build(project);
        	String devNull = onMsftWindows()? "nul" : "/dev/null";
            project.getScm().checkout(new FreeStyleBuild(project), null, 
            		new FilePath(projectDir), new StreamBuildListener(System.err), new File(devNull));
            System.gc();
            System.out.println(mmx.getHeapMemoryUsage());
        }

        System.out.println(mmx.getHeapMemoryUsage());
    }
}
