package org.auxis.maven.materialplugin;

import io.takari.maven.testing.TestMavenRuntime;
import io.takari.maven.testing.TestResources;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.assertFilesPresent;

/**
 *
 */
public class CreateBillTest {
    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final TestMavenRuntime maven = new TestMavenRuntime();

    @Test
    public void test() throws Exception {
        File basedir = resources.getBasedir("testproject1");
        maven.executeMojo(basedir, "test", newParameter("bill", "target/output.txt"));
        assertFilesPresent(basedir, "target/output.txt");
    }
}
