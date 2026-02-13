package org.dvsa.testing.framework;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.config.AppConfig;
import org.dvsa.testing.framework.browser.PlayWrightManager;
import org.dvsa.testing.framework.jsoup.SpiderCrawler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.dvsa.testing.framework.axe.AXEScanner.generateFinalReport;
import static org.dvsa.testing.framework.axe.AXEScanner.getAllViolations;
import static org.dvsa.testing.framework.bots.AnswerBot.formAutoFill;
import static org.dvsa.testing.framework.otp.Generator.generatePin;


public class PageCrawlerAnswerBotTest {

    private static final Logger LOGGER = LogManager.getLogger(PageCrawlerAnswerBotTest.class);

    private String baseURL;
    private static Map<String, String> cookies;
    private static Page page;
    private static PlayWrightManager browserManager;

    public static Map<String, String> getCookies() {
        return cookies;
    }

    public static void setCookies(Map<String, String> cookies) {
        PageCrawlerAnswerBotTest.cookies = cookies;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    @BeforeAll
    public static void browserSetUp() {
        getAllViolations().clear();
        browserManager = new PlayWrightManager();
        String browserName = System.getProperty("browser");
        if (browserName == null) {
            browserName = "chrome";
        }
        browserManager.selectBrowser(browserName);
        page = browserManager.getPage();
    }

    @Test
    public void mtsRandomAnswerAndCrawlerScanner() {
        String[] urls = AppConfig.getBaseUrls();
        String url = urls[1];
        page.navigate(url);
        login();
        formAutoFill(page, page.url(), AppConfig.getString("domain"), true);
        SpiderCrawler.crawler(1, page.url(), new HashSet<>(), page);
    }


    private void login() {
        Locator cookieAccept = page.locator("//*[contains(text(),'Accept additional cookies')]");
        Locator usernameInput = page.locator("input[id='username']");
        Locator passwordInput = page.locator("input[id='password']");
        Locator submitButton = page.locator("input[type='submit']");
        Locator otpCodeInput = page.locator("input[id='otp-code']");
        Locator pinInput = page.locator("input[id='pin']");

        if (cookieAccept.isVisible()) {
            cookieAccept.click();
        }

        if (usernameInput.isVisible()) {
            usernameInput.fill(AppConfig.getString("username"));
        }
        if (passwordInput.isVisible()) {
            passwordInput.fill(AppConfig.getString("password"));
        }
        if (submitButton.isVisible()) {
            submitButton.click();
        }
        if (otpCodeInput.isVisible()) {
            otpCodeInput.fill(generatePin(AppConfig.getString("authKey")));
        }
        if (pinInput.isVisible()) {
            pinInput.fill(generatePin(AppConfig.getString("authKey")));
        }
        if (submitButton.isVisible()) {
            submitButton.click();
        }

        setCookies();
    }

    private static void setCookies() {
        List<Cookie> playwrightCookies = page.context().cookies();
        Map<String, String> jsoupCookies = new HashMap<>();
        for (Cookie cookie : playwrightCookies) {
            jsoupCookies.put(cookie.name, cookie.value);
        }
        setCookies(jsoupCookies);
    }

    @AfterAll
    public static void testAfter() {
        try {
            LOGGER.info("Starting AI-backed accessibility analysis...");
            generateFinalReport();
        } catch (Exception e) {
            LOGGER.error("AI Analysis failed: {}", e.getMessage());
        } finally {
            browserManager.closeBrowserAndPage();
        }
    }
}