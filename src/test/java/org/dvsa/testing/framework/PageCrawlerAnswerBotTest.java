package org.dvsa.testing.framework;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.typesafe.config.*;
import org.dvsa.testing.framework.browser.PlayWrightManager;
import org.dvsa.testing.framework.jsoup.SpiderCrawler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.dvsa.testing.framework.axe.AXEScanner.generateFinalReport;
import static org.dvsa.testing.framework.bots.AnswerBot.formAutoFill;
import static org.dvsa.testing.framework.otp.Generator.generatePin;


public class PageCrawlerAnswerBotTest {

    private static final Config config = ConfigFactory.defaultApplication();
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
        browserManager = new PlayWrightManager();
        browserManager.selectBrowser(System.getProperty("browser"));
        page = browserManager.getPage();
    }

    @Test
    public void mtsRandomAnswerAndCrawlerScanner() throws IOException {
        setBaseURL(config.getString("mtsBaseURL"));
        page.navigate(getBaseURL());
        Page page = login();
        SpiderCrawler.crawler(1, page.url(), new ArrayList<>(), page);
        formAutoFill(page, page.url());
    }

    @Test
    public void mothRandomAnswerAndCrawlerScanner() {
        setBaseURL(config.getString("mothBaseURL"));
        page.navigate(getBaseURL());
        setCookies();
        formAutoFill(page, page.url());
        SpiderCrawler.crawler(1, page.url(), new ArrayList<>(), page);
    }

    private Page login() {
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
            usernameInput.fill(config.getString("username"));
        }
        if (passwordInput.isVisible()) {
            passwordInput.fill(config.getString("password"));
        }
        if (submitButton.isVisible()) {
            submitButton.click();
        }
        if (otpCodeInput.isVisible()) {
            otpCodeInput.fill(generatePin(config.getString("authKey")));
        }
        if (pinInput.isVisible()) {
            pinInput.fill(generatePin(config.getString("authKey")));
        }
        if (submitButton.isVisible()) {
            submitButton.click();
        }

        setCookies();
        return page;
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
        generateFinalReport();
        browserManager.closeBrowserAndPage();
    }
}