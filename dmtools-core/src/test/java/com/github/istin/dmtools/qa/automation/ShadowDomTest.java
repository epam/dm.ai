package com.github.istin.dmtools.qa.automation;

import com.github.istin.dmtools.common.utils.HtmlCleaner;
import com.github.istin.dmtools.qa.automation.playwright.PlaywrightBridge;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore("Disabled - Playwright integration test causing issues in CI")
public class ShadowDomTest {
    private AutomationTester tester;
    private String testPageUrl;

    @Before
    public void setUp() {
        URL resourceUrl = getClass().getClassLoader().getResource("test-shadow-dom.html");
        assertNotNull("Test page resource not found", resourceUrl);
        testPageUrl = resourceUrl.toString();
        tester = new AutomationTester(testPageUrl, new PlaywrightBridge(true));
    }

    @Test
    public void testClearingOfPage() {
        String screenSource = tester.getScreenSource();
        System.out.println(screenSource);
        System.out.println(" ====== cleared ===== ");
        System.out.println(HtmlCleaner.cleanOnlyStylesAndSizes(screenSource));
    }

    @After
    public void tearDown() {
        if (tester != null) {
            tester.close();
        }
    }

    @Test
    public void testRegularDomInteraction() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute(( exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    return "bridge.click('#regular-button')";
                case 1:
                    String html = tester.getScreenSource();
                    assertTrue("Regular button click result not found",
                            html.contains("Regular button clicked!"));
                    return null;
                default:
                    return null;
            }
        });
    }

    //TODO fix me!
//    @Test
//    public void testShadowDomButtonClick() {
//        AtomicInteger step = new AtomicInteger(0);
//
//        tester.execute((exception) -> {
//            switch (step.getAndIncrement()) {
//                case 0:
//                    return "bridge.click(bridge.findElementByText('Accept All'))";
//                case 1:
//                    String html = tester.getScreenSource();
//                    assertTrue("Shadow DOM button click result not found",
//                            html.contains("Cookie preference: Accept All"));
//                    return null;
//                default:
//                    return null;
//            }
//        });
//    }

//TODO fix me!
//    @Test
//    public void testShadowDomSecondaryButtonClick() {
//        AtomicInteger step = new AtomicInteger(0);
//
//        tester.execute(( exception) -> {
//            switch (step.getAndIncrement()) {
//                case 0:
//                    // Attempt the normal click via the bridge.
//                    return "bridge.click(bridge.findElementByText('No, thanks'))";
//                case 1:
//                    // Now verify that the expected result is in the page source.
//                    String html = tester.getScreenSource();
//                    assertTrue("Shadow DOM secondary button click result not found",
//                            html.contains("Cookie preference: No, thanks"));
//                    return null;
//                default:
//                    return null;
//            }
//        });
//    }


    @Test
    public void testGetScreenSource() {
        AtomicInteger step = new AtomicInteger(0);

        tester.execute((exception) -> {
            switch (step.getAndIncrement()) {
                case 0:
                    String html = tester.getScreenSource();
                    assertNotNull(html);
                    assertTrue(html.contains("cookie-consent-widget"));
                    assertTrue(html.contains("Yes, that's fine")); // Verify shadow DOM content is included
                    return null;
                default:
                    return null;
            }
        });
    }
}