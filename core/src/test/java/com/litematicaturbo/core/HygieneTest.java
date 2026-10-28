package com.litematicaturbo.core;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The in-game tests find buttons, options and windows by name, label or what they do - never by a fixed screen position
 * or window size (layouts, UI scale and window sizes change). This fails the build when a test file clicks at numbers,
 * moves the mouse, or drives the machine's real mouse / keyboard.
 */
public class HygieneTest
{
    private static final Pattern FIXED_POSITION = Pattern.compile(
        "mouse(Clicked|Released|Dragged|Moved)\\s*\\(\\s*-?[0-9]"
        + "|click(At)?\\s*\\(\\s*-?[0-9]+\\s*,\\s*-?[0-9]+"
        + "|java[.]awt[.]Robot|new\\s+Robot\\s*\\(|mouseMove\\s*\\(|setCursorPos|glfwSetCursorPos|setWindowSize|setSize\\s*\\(");

    private static Path testsFolder()
    {
        for (Path p : new Path[] {Paths.get("selftest/src"), Paths.get("../selftest/src")})
        {
            if (Files.isDirectory(p))
            {
                return p;
            }
        }

        return null;
    }

    @Test
    public void inGameTestsNeverClickAtFixedPositions() throws IOException
    {
        Path folder = testsFolder();
        assertTrue("the in-game tests (selftest/src) were not found from " + Paths.get("").toAbsolutePath(), folder != null);
        List<String> found = new ArrayList<>();

        try (Stream<Path> files = Files.walk(folder))
        {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList()))
            {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

                for (int i = 0; i < lines.size(); i++)
                {
                    String line = lines.get(i).trim();

                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*"))
                    {
                        continue;
                    }

                    if (FIXED_POSITION.matcher(line).find())
                    {
                        found.add(file.getFileName() + ":" + (i + 1) + ": " + line);
                    }
                }
            }
        }

        if (found.isEmpty() == false)
        {
            fail("in-game tests with fixed positions / the real mouse: " + found);
        }
    }

    @Test
    public void theCheckSeesWhatItShouldCatch()
    {
        assertTrue(FIXED_POSITION.matcher("screen.mouseClicked(120, 80, 0);").find());
        assertTrue(FIXED_POSITION.matcher("click(10, 20);").find());
        assertTrue(FIXED_POSITION.matcher("new Robot().mouseMove(1, 2);").find());
        assertTrue(FIXED_POSITION.matcher("button.onMouseClicked(x, y, 0)").find() == false);
        assertTrue(FIXED_POSITION.matcher("click(button);").find() == false);
    }
}
