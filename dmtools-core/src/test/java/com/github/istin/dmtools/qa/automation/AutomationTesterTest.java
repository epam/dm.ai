package com.github.istin.dmtools.qa.automation;

import com.github.istin.dmtools.qa.automation.playwright.PlaywrightBridge;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore("Disabled - Playwright integration test causing issues in CI")
public class AutomationTesterTest {
    private AutomationTester tester;
    private String testPageUrl;

    @Before
    public void setUp() {
        URL resourceUrl = getClass().getClassLoader().getResource("test-page.html");
        assertNotNull("Test page resource not found", resourceUrl);
        testPageUrl = resourceUrl.toString();
        tester = new AutomationTester(testPageUrl, new PlaywrightBridge(true));
    }

    @After
    public void tearDown() {
        if (tester != null) {
            tester.close();
        }
    }

    @Test
    public void testNavigateAndGetTitle() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.getTitle().then(title => window.lastTitle = title)";
                case 1:
                    assertTrue(tester.getScreenSource().contains("<title>Bridge Test Page</title>"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testFindElementAndClick() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.click('#clickButton')";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Button clicked!"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testFindElementByTextAndClick() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.findElementByText('Click Me').then(element => bridge.click(element))";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Button clicked!"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testSendKeys() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.sendKeys('#textInput', 'Test Input')";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Input value: Test Input"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testSelectOption() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.selectOption('#dropdown', 'Option 1')";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Selected: 1"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testWaitForElement() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.click('#dynamicButton')";
                case 1:
                    return "bridge.waitForElement('#dynamicContent:not(:empty)', 5)";
                case 2:
                    assertTrue(tester.getScreenSource().contains("Dynamic content loaded!"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testScroll() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.scroll('down')";
                case 1:
                    return "bridge.scroll('up')";
                case 2:
                    return "bridge.scroll('middle')";
                case 3:
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testScrollToElement() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.scrollToElement('#scrollTarget')";
                case 1:
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testTypeAndSelect() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.typeAndSelect('#autocomplete', 'Option One')";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Option One"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testExecuteDynamicJavascript() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.executeDynamicJavascript('document.getElementById(\\'clickResult\\').textContent = \\'Dynamic JS executed!\\';')";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Dynamic JS executed!"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testShadowDOMInteractions() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.findElementByText('Shadow DOM Button').then(element => bridge.click(element))";
                case 1:
                    assertTrue(tester.getScreenSource().contains("Shadow button clicked!"));
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testGetScreenSource() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    String html = tester.getScreenSource();
                    assertNotNull(html);
                    assertTrue(html.contains("<title>Bridge Test Page</title>"));
                    assertTrue(html.contains("Shadow DOM Button")); // Verify shadow DOM content is included
                    return null;
                default:
                    return null;
            }
        });
    }

    @Test
    public void testTakeScreenshot() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    File screenshot = tester.takeScreenshot();
                    assertNotNull(screenshot);
                    assertTrue(screenshot.exists());
                    assertTrue(screenshot.length() > 0);
                    return null;
                default:
                    return null;
            }
        });
    }
}