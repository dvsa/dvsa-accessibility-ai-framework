package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.axe.AXEScanner;
import org.dvsa.testing.framework.utils.DomainValidator;
import org.dvsa.testing.framework.config.AppConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class AnswerBot {
    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);


    public static void formAutoFill(Object driver, String url, String baseDomain, boolean allowSubdomains) {
        if (driver instanceof Page page) {
            runPlaywrightFormFill(page, url, baseDomain, allowSubdomains);
        } else if (driver instanceof WebDriver seleniumDriver) {
            runSeleniumFormFill(seleniumDriver, url, baseDomain, allowSubdomains);
        }
    }

    private static void runPlaywrightFormFill(Page page, String url, String baseDomain, boolean allowSubdomains) {
        try {
            if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) return;

            page.navigate(url);
            page.waitForLoadState(LoadState.LOAD);
            handleCookieBanners(page);

            int attempts = 0;
            int maxAttempts = 10;

            while (attempts < maxAttempts) {
                if (!DomainValidator.isSameDomain(page.url(), baseDomain, allowSubdomains)) break;

                List<ElementHandle> inputs = page.querySelectorAll("input:not([type='hidden'])");
                for (ElementHandle element : inputs) {
                    if (element.isDisabled() || !element.isVisible()) continue;

                    String name = element.getAttribute("name");
                    if (name == null) continue;

                    Locator locator = page.locator("input[name='" + name + "']").first();
                    String type = element.getAttribute("type");
                    fillLogic(locator, type != null ? type : "text");
                }

                List<Locator> buttons = getAllClickableButtons(page).stream()
                        .filter(btn -> isSafeButton(btn, baseDomain, allowSubdomains))
                        .toList();

                if (buttons.isEmpty()) break;

                Locator selectedButton = buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));
                selectedButton.evaluate("el => el.removeAttribute('target')"); // Keep in same tab

                selectedButton.click(new Locator.ClickOptions().setTimeout(5000));
                page.waitForTimeout(1000);

                AXEScanner.scan(page);
                attempts++;
            }
        } catch (Exception e) {
            LOGGER.error("Playwright Form fill failed", e);
        }
    }

    private static void fillLogic(Locator locator, String type) {
        switch (type != null ? type : "text") {
            case "radio" -> handleRadioButton(locator);
            case "checkbox" -> {
                if (!locator.isChecked()) locator.check();
            }
            case "number" -> locator.fill(String.valueOf(ThreadLocalRandom.current().nextInt(1, 9999)));
            default -> handleTextInput(locator);
        }
    }

    private static void handleTextInput(Locator locator) {
        String name = Objects.requireNonNullElse(locator.getAttribute("name"), "").toLowerCase();
        String valueToFill;

        if (name.contains("email")) {
            valueToFill = "test@dvsa.gov.uk";
        } else if (name.contains("postcode")) {
            valueToFill = "NG2 1AY";
        } else if (name.contains("registration")) {
            valueToFill = AppConfig.getString("registration");
        } else {
            valueToFill = RandomStringUtils.secure().nextAlphabetic(8);
        }

        locator.fill(valueToFill);
    }

    private static void handleRadioButton(Locator locator) {
        List<Locator> options = locator.all();
        if (!options.isEmpty()) {
            options.get(ThreadLocalRandom.current().nextInt(options.size())).check();
        }
    }

    private static void handleCookieBanners(Page page) {
        try {
            Locator accept = page.locator("button:has-text('Accept'), button:has-text('Agree')").first();
            if (accept.isVisible()) accept.click();
        } catch (Exception ignored) {
        }
    }

    private static boolean isSafeButton(Locator button, String baseDomain, boolean allowSubdomains) {
        try {
            String text = button.textContent().toLowerCase();
            String href = button.getAttribute("href");
            if (text.contains("logout") || text.contains("sign out")) return false;
            if (href != null && href.startsWith("http")) {
                return DomainValidator.isSameDomain(href, baseDomain, allowSubdomains);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Locator> getAllClickableButtons(Page page) {
        return page.locator("button:visible:enabled, input[type='submit']:visible:enabled, a.govuk-button").all();
    }

    private static void runSeleniumFormFill(WebDriver driver, String url, String baseDomain, boolean allowSubdomains) {
        try {
            if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) return;

            driver.get(url);
            handleCookieBannersSelenium(driver);

            int attempts = 0;
            while (attempts < 5) {
                if (!DomainValidator.isSameDomain(driver.getCurrentUrl(), baseDomain, allowSubdomains)) break;

                List<WebElement> inputs = driver.findElements(By.cssSelector("input:not([type='hidden'])"));
                for (WebElement element : inputs) {
                    if (!element.isDisplayed() || !element.isEnabled()) continue;

                    String type = element.getAttribute("type");
                    String name = element.getAttribute("name");
                    if (name == null) continue;

                    seleniumFillLogic(element, type != null ? type : "text");
                }

                List<WebElement> buttons = driver.findElements(By.cssSelector("button, input[type='submit'], .govuk-button"))
                        .stream()
                        .filter(btn -> isSafeButtonSelenium(btn, baseDomain, allowSubdomains))
                        .toList();

                if (buttons.isEmpty()) break;

                WebElement selectedButton = buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));

                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].removeAttribute('target')", selectedButton);

                selectedButton.click();

                AXEScanner.scan(driver);
                attempts++;
            }
        } catch (Exception e) {
            LOGGER.error("Selenium Form fill failed", e);
        }
    }

    private static void seleniumFillLogic(WebElement element, String type) {
        switch (type) {
            case "radio", "checkbox" -> {
                if (!element.isSelected()) element.click();
            }
            case "number" -> element.sendKeys(String.valueOf(ThreadLocalRandom.current().nextInt(1, 9999)));
            default -> {
                String name = Objects.requireNonNull(element.getAttribute("name")).toLowerCase();
                String value;
                if (name.contains("email")) value = "test@dvsa.gov.uk";
                else if (name.contains("postcode")) value = "NG2 1AY";
                else value = RandomStringUtils.secure().nextAlphabetic(8);

                element.clear();
                element.sendKeys(value);
            }
        }
    }

    private static boolean isSafeButtonSelenium(WebElement button, String baseDomain, boolean allowSubdomains) {
        try {
            if (!button.isDisplayed() || !button.isEnabled()) {
                return false;
            }

            String text = button.getText().toLowerCase();
            String href = button.getAttribute("href");
            String type = button.getAttribute("type");

            if (text.contains("sign out") || text.contains("logout") || text.contains("log out")) {
                return false;
            }

            if (text.contains("remove") || text.contains("delete") || text.contains("cookie")) {
                return false;
            }

            if (href != null && !href.startsWith("#") && !href.startsWith("/")) {
                return DomainValidator.isSameDomain(href, baseDomain, allowSubdomains);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void handleCookieBannersSelenium(WebDriver driver) {
        String[] xpathSelectors = {
                "//button[contains(text(), 'Accept')]",
                "//button[contains(text(), 'Agree')]",
                "//button[contains(text(), 'Accept additional cookies')]",
                "//*[@id='cookie-accept']",
                "//a[contains(@class, 'govuk-button') and contains(text(), 'Accept')]"
        };

        for (String xpath : xpathSelectors) {
            try {
                List<WebElement> banners = driver.findElements(By.xpath(xpath));
                if (!banners.isEmpty() && banners.get(0).isDisplayed()) {
                    banners.get(0).click();
                    LOGGER.info("Accepted cookie banner via Selenium: {}", xpath);
                    Thread.sleep(500);
                }
            } catch (Exception ignored) {
                // We ignore errors here so the bot keeps trying to fill the actual form
            }
        }
    }
}