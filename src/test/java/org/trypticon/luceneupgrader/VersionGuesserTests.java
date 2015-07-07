package org.trypticon.luceneupgrader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link VersionGuesser}.
 */
@RunWith(Parameterized.class)
public class VersionGuesserTests {
    private final String version;
    private final String variant;
    private final LuceneVersion expected;
    private Path temp;

    public VersionGuesserTests(String version, String variant, LuceneVersion expected) {
        this.version = version;
        this.variant = variant;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        List<Object[]> data = new LinkedList<>();
        List<String> variants = Arrays.asList("empty", "nonempty");
        for (String version : TestIndices.allVersions()) {
            LuceneVersion expected;
            if (version.startsWith("2.")) {
                expected = LuceneVersion.VERSION_2;
            } else if (version.startsWith("3.")) {
                if (version.startsWith("3.0")) {
                    // Pretend that 3.0 is v2.
                    expected = LuceneVersion.VERSION_2;
                } else {
                    expected = LuceneVersion.VERSION_3;
                }
            } else if (version.startsWith("4.")) {
                expected = LuceneVersion.VERSION_4;
            } else if (version.startsWith("5.")) {
                expected = LuceneVersion.VERSION_5;
            } else {
                throw new IllegalStateException("Didn't add a new case when you added a new version");
            }

            for (String variant : variants) {
                data.add(new Object[]{ version, variant, expected });
            }
        }
        return data;
    }

    @Before
    public void setUp() throws Exception {
        temp = Files.createTempDirectory("test");
    }

    @Test
    public void test() throws Exception {
        TestIndices.explodeZip(version, variant, temp);
        LuceneVersion guessed = new VersionGuesser().guess(temp);
        assertThat(guessed, is(expected));
    }
}
