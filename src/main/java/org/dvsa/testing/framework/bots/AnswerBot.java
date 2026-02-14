package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.axe.AXEScanner;
import org.dvsa.testing.framework.utils.DomainValidator;
import org.dvsa.testing.framework.config.AppConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AnswerBot {
    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    public static void formAutoFill(Object driver, String url, String baseDomain, boolean allowSubdomains) {
        if (driver instanceof Page page) {
            runPlaywrightFormFill(page, url, baseDomain, allowSubdomains);
        } else if (driver instanceof WebDriver seleniumDriver) {
            runSeleniumFormFill(seleniumDriver, url, baseDomain, allowSubdomains);
        }
    }

    // --- PLAYWRIGHT ENGINE ---
    private static void runPlaywrightFormFill(Page page, String url, String baseDomain, boolean allowSubdomains) {
        try {
            page.navigate(url);
            page.waitForLoadState(LoadState.LOAD);
            page.waitForLoadState(LoadState.LOAD);
            handleCookieBanners(page);
            int maxDropdownPasses = getDropdownMaxPasses();
            int playwrightDropdownSettleMs = getPlaywrightDropdownSettleMs();

            int attempts = 0;
            while (attempts < 1000) {
                if (!DomainValidator.isSameDomain(page.url(), baseDomain, allowSubdomains)) {
                    LOGGER.info("Reached boundary or external redirect: {}", page.url());
                    break;
                }

                page.querySelectorAll("input[type='text'], input[type='number'], input:not([type])").forEach(el -> {
                    if (!el.isVisible() || el.isDisabled()) return;
                    String name = el.getAttribute("name");
                    if (name != null) fillLogicPlaywright(page.locator("input[name='" + name + "']").first(), "text");
                });

                page.querySelectorAll("input[type='radio'], input[type='checkbox']").forEach(element -> {
                    if (element.isDisabled() || !element.isVisible()) return;

                    String name = element.getAttribute("name");
                    Locator radioGroup = page.locator(String.format("input[name='%s']", name));

                    try {
                        List<Locator> options = radioGroup.all();
                        int randomIndex = ThreadLocalRandom.current().nextInt(options.size());
                        Locator selected = options.get(randomIndex);

                        if (!selected.isChecked()) {
                            selected.check(new Locator.CheckOptions().setForce(true));
                            selected.dispatchEvent("change");
                            LOGGER.info("Checked radio button group: {} (Option index {})", name, randomIndex);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Could not check radio button {}: {}", name, e.getMessage());
                    }
                });

                for (int pass = 0; pass < maxDropdownPasses; pass++) {
                    Locator selects = page.locator("select:visible");
                    int total = selects.count();
                    int changed = 0;

                    for (int i = 0; i < total; i++) {
                        Locator select = selects.nth(i);
                        if (select.isDisabled()) continue;

                        String currentValue = Objects.toString(select.inputValue(), "");
                        if (!currentValue.isBlank()) continue;

                        Locator enabledOptions = select.locator("option:not([disabled])");
                        int optionCount = enabledOptions.count();
                        if (optionCount <= 1) continue;

                        int optionIndex = ThreadLocalRandom.current().nextInt(1, optionCount);
                        String value = enabledOptions.nth(optionIndex).getAttribute("value");
                        if (value == null || value.isBlank()) continue;

                        select.selectOption(value);
                        select.dispatchEvent("change");
                        changed++;
                        LOGGER.info("Selected dropdown on pass {}: option index {}", pass, optionIndex);

                        if (playwrightDropdownSettleMs > 0) {
                            try {
                                page.waitForLoadState(
                                        LoadState.NETWORKIDLE,
                                        new Page.WaitForLoadStateOptions().setTimeout(playwrightDropdownSettleMs)
                                );
                            } catch (Exception ignored) {}
                        }
                    }

                    if (changed == 0) break;
                }

                List<Locator> buttons = getAllClickableButtons(page);
                if (buttons.isEmpty()) {
                    LOGGER.info("No more action buttons found on {}", page.url());
                    break;
                }

                Locator btn = buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));
                LOGGER.info("Attempting click on: [{}]", btn.textContent());

                btn.evaluate("el => el.removeAttribute('target')");
                btn.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));

                page.waitForLoadState(LoadState.NETWORKIDLE);
                AXEScanner.scan(page);

                attempts++;
            }
        } catch (Exception e) {
            LOGGER.error("Playwright Bot failed: {}", e.getMessage());
        }
    }

    // --- SELENIUM ENGINE ---
    private static void runSeleniumFormFill(WebDriver driver, String url, String baseDomain, boolean allowSubdomains) {
        try {
            driver.get(url);
            handleCookieBannersSelenium(driver);
            int maxDropdownPasses = getDropdownMaxPasses();
            int seleniumDropdownSettleMs = getSeleniumDropdownSettleMs();

            int attempts = 0;
            while (attempts < 100) {
                String oldUrl = driver.getCurrentUrl();
                if (!DomainValidator.isSameDomain(oldUrl, baseDomain, allowSubdomains)) break;

                driver.findElements(By.cssSelector("input:not([type='hidden'])")).forEach(element -> {
                    if (element.isDisplayed() && element.isEnabled()) fillLogicSelenium(element, element.getAttribute("type"));
                });

                fillDependentDropdownsSelenium(driver, maxDropdownPasses, seleniumDropdownSettleMs);

                List<WebElement> buttons = getAllClickableButtonsSelenium(driver);

                if (buttons.isEmpty()) break;

                WebElement selectedButton = buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));

                performSeleniumClick(driver, selectedButton);

                try {
                    new WebDriverWait(driver, Duration.ofSeconds(3)).until(ExpectedConditions.not(ExpectedConditions.urlToBe(oldUrl)));
                } catch (Exception ignored) {}

                AXEScanner.scan(driver);
                attempts++;
            }
        } catch (Exception e) {
            LOGGER.error("Selenium Bot failed: {}", e.getMessage());
        }
    }

    private static void fillDependentDropdownsSelenium(WebDriver driver, int maxDropdownPasses, int settleMs) {
        for (int pass = 0; pass < maxDropdownPasses; pass++) {
            int changed = 0;
            List<WebElement> selects = driver.findElements(By.tagName("select"));

            for (WebElement selectEl : selects) {
                try {
                    if (!selectEl.isDisplayed() || !selectEl.isEnabled()) continue;

                    Select select = new Select(selectEl);
                    List<WebElement> options = select.getOptions();
                    if (options.size() <= 1) continue;

                    int selectedIndex = select.getAllSelectedOptions().isEmpty()
                            ? 0
                            : options.indexOf(select.getFirstSelectedOption());
                    if (selectedIndex > 0) continue;

                    List<Integer> candidateIndexes = new ArrayList<>();
                    for (int i = 1; i < options.size(); i++) {
                        WebElement option = options.get(i);
                        String value = Objects.toString(option.getAttribute("value"), "").trim();
                        if (option.isEnabled() && !value.isBlank()) candidateIndexes.add(i);
                    }

                    if (candidateIndexes.isEmpty()) continue;

                    int pick = candidateIndexes.get(ThreadLocalRandom.current().nextInt(candidateIndexes.size()));
                    select.selectByIndex(pick);
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                            selectEl
                    );
                    changed++;
                    LOGGER.info("Selected Selenium dropdown on pass {}: option index {}", pass, pick);
                } catch (StaleElementReferenceException ignored) {
                } catch (Exception e) {
                    LOGGER.warn("Could not select Selenium dropdown: {}", e.getMessage());
                }
            }

            if (changed == 0) break;
            if (settleMs > 0) {
                try {
                    Thread.sleep(settleMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static void performSeleniumClick(WebDriver driver, WebElement element) {
        String text = element.getText();
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
            ((JavascriptExecutor) driver).executeScript("arguments[0].removeAttribute('target');", element);

            try {
                element.click();
            } catch (Exception e) {
                new Actions(driver).moveToElement(element).click().perform();
            }
        } catch (Exception finalEx) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
        LOGGER.info("Clicked Selenium button: [{}]", text);
    }

    private static String generateValue(String fieldName) {
        String name = (fieldName != null) ? fieldName.toLowerCase() : "";
        if (name.contains("email")) return "test@dvsa.gov.uk";
        if (name.contains("postcode")) return "NG2 1AY";
        if (name.contains("vin")) return AppConfig.getString("vin");
        if (name.contains("registration")) return AppConfig.getString("registration");
        return RandomStringUtils.secure().nextAlphabetic(8);
    }

    public static List<Locator> getAllClickableButtons(Page page) {
        String selectors = "button:visible:enabled, input[type='submit']:visible:enabled, a:visible.govuk-button, [role='button']:visible";
        List<Locator> all = new ArrayList<>(page.locator(selectors).all());

        String[] keywords = {"submit", "continue", "next", "start", "confirm", "save"};
        for (String k : keywords) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(k, java.util.regex.Pattern.CASE_INSENSITIVE);

            all.addAll(page.locator("button:visible").filter(new Locator.FilterOptions().setHasText(pattern)).all());
            all.addAll(page.locator("a:visible").filter(new Locator.FilterOptions().setHasText(pattern)).all());
        }

        return all.stream()
                .distinct()
                .filter(AnswerBot::isValidBtnPlaywright)
                .collect(Collectors.toList());
    }

    private static List<WebElement> getAllClickableButtonsSelenium(WebDriver driver) {
        String css = "button:enabled, input[type='submit'], .govuk-button, [role='button']";
        return driver.findElements(By.cssSelector(css)).stream()
                .filter(e -> e.isDisplayed() && e.getSize().getHeight() > 0)
                .filter(AnswerBot::isSafeButtonSelenium)
                .collect(Collectors.toList());
    }

    // --- HELPERS ---
    private static boolean isValidBtnPlaywright(Locator b) {
        try {
            String txt = Objects.requireNonNullElse(b.textContent(), "").toLowerCase();
            String val = Objects.requireNonNullElse(b.getAttribute("value"), "").toLowerCase();
            boolean isForbidden = txt.contains("logout") || txt.contains("sign out") || txt.contains("delete") || txt.contains("remove");
            return b.isVisible() && !isForbidden && !val.contains("logout");
        } catch (Exception e) { return false; }
    }

    private static boolean isSafeButtonSelenium(WebElement b) {
        try {
            String txt = b.getText().toLowerCase();
            boolean isForbidden = txt.contains("logout") || txt.contains("sign out") || txt.contains("delete") || txt.contains("remove");
            return b.isDisplayed() && !isForbidden;
        } catch (Exception e) { return false; }
    }


    private static void fillLogicPlaywright(Locator loc, String type) {
        String t = (type != null) ? type : "text";
        if (t.equals("radio")) {
            List<Locator> ops = loc.all();
            if (!ops.isEmpty()) ops.get(ThreadLocalRandom.current().nextInt(ops.size())).check();
        } else if (t.equals("checkbox")) { if (!loc.isChecked()) loc.check(); }
        else { loc.fill(generateValue(loc.getAttribute("name"))); }
    }

    private static void fillLogicSelenium(WebElement el, String type) {
        String t = (type != null) ? type : "text";
        if (t.equals("radio") || t.equals("checkbox")) { if (!el.isSelected()) el.click(); }
        else { el.clear(); el.sendKeys(generateValue(el.getAttribute("name"))); }
    }

    private static void handleCookieBanners(Page page) {
        try {
            Locator btn = page.locator("button:has-text('Accept'), .govuk-cookie-banner__button-accept").first();
            if (btn.isVisible()) btn.click();
        } catch (Exception ignored) {}
    }

    private static void handleCookieBannersSelenium(WebDriver driver) {
        try {
            WebElement btn = driver.findElement(By.xpath("//button[contains(text(),'Accept')]"));
            if (btn.isDisplayed()) btn.click();
        } catch (Exception ignored) {}
    }

    private static int getDropdownMaxPasses() {
        return Math.max(1, AppConfig.getInt("answerbot.dropdown.maxPasses", 4));
    }

    private static int getPlaywrightDropdownSettleMs() {
        return Math.max(0, AppConfig.getInt("answerbot.playwright.dropdown.settleMs", 1000));
    }

    private static int getSeleniumDropdownSettleMs() {
        return Math.max(0, AppConfig.getInt("answerbot.selenium.dropdown.settleMs", 200));
    }
}
