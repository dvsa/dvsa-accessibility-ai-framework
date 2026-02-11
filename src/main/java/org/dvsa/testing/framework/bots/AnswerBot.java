package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.dvsa.testing.framework.config.AppConfig;
import org.dvsa.testing.framework.utils.DomainValidator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.dvsa.testing.framework.browser.PlayWrightWaits.waitAndEnterText;


public class AnswerBot {
    static List<ElementHandle> inputElements;

    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    public static void formAutoFill(Page page, String url, String baseDomain, boolean allowSubdomains) {
    if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) {
        LOGGER.warn("Skipping form fill: URL {} is outside domain {}", url, baseDomain);
        return;
    }

    page.context().onPage(newPage -> {
        try {
            newPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            if (!DomainValidator.isSameDomain(newPage.url(), baseDomain, allowSubdomains)) {
                LOGGER.info("Closing external popup: {}", newPage.url());
                newPage.close();
            }
        } catch (Exception e) { /* Tab might already be closed */ }
    });

    try {
        page.navigate(url);
        page.waitForLoadState(LoadState.LOAD);

        // handleCookieBanners(page); // Disabled per user request - avoid clicking on cookies

        int attempts = 0;
        int maxAttempts = 10;

        while (attempts < maxAttempts) {
            if (!DomainValidator.isSameDomain(page.url(), baseDomain, allowSubdomains)) {
                LOGGER.warn("Redirected to external site {}. Aborting form fill.", page.url());
                return;
            }

            try {
                List<ElementHandle> inputElements = page.querySelectorAll("input");

                for (ElementHandle element : inputElements) {
                    if (element == null || element.isDisabled() || element.isHidden()) continue;
                    
                    String type = element.getAttribute("type");
                    String name = element.getAttribute("name");
                    if (name == null) continue;

                    Locator locator = page.locator("input[name='" + name + "']").first();

                    switch (type != null ? type : "text") {
                        case "radio" -> handleRadioButton(locator, element);
                        case "text", "password" -> handleTextInput(page, locator);
                        case "number" -> waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 9999)));
                        case "checkbox" -> {
                            if (!(boolean) element.evaluate("el => el.checked")) element.click();
                        }
                    }
                }

                List<Locator> buttons = getAllClickableButtons(page).stream()
                        .filter(btn -> isSafeButton(btn, baseDomain, allowSubdomains))
                        .toList();
                        
                if (buttons.isEmpty()) return;

                Locator selectedButton = buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));
                
                selectedButton.evaluate("el => el.removeAttribute('target')");
                
                selectedButton.click(new Locator.ClickOptions().setTimeout(3000));
                page.waitForTimeout(500);

            } catch (PlaywrightException e) {
                LOGGER.info("Retrying due to: {}", e.getMessage());
                if (++attempts >= maxAttempts) break;
            }
        }
    } catch (Exception e) {
        LOGGER.error("Form fill failed", e);
    }
}

private static boolean isSafeButton(Locator button, String baseDomain, boolean allowSubdomains) {
    try {
        String text = button.textContent().toLowerCase();
        String href = button.getAttribute("href");
        
        if (text.contains("sign out") || text.contains("logout")) return false;
        
        // Avoid clicking on any cookies-related buttons or links
        if (text.contains("cookie") || text.contains("accept cookies") || 
            text.contains("reject cookies") || text.contains("manage cookies") ||
            text.contains("cookie preferences") || text.contains("cookie settings")) return false;
        
        if (href != null && !href.startsWith("#") && !href.startsWith("/")) {
            if (!DomainValidator.isSameDomain(href, baseDomain, allowSubdomains)) return false;
        }
        
        return true;
    } catch (Exception e) { return false; }
}

private static void handleCookieBanners(Page page) {
    String[] commonSelectors = {"button:has-text('Accept')", "button:has-text('Agree')", "#cookie-accept", ".optanon-allow-all"};
    for (String selector : commonSelectors) {
        try {
            if (page.locator(selector).isVisible()) {
                page.locator(selector).click();
                page.waitForTimeout(500);
            }
        } catch (Exception ignored) {}
    }
}

        private static void handleRadioButton(Locator locator, ElementHandle element) {
        if ((boolean) element.evaluate("el => el.checked")) return;

        List<Locator> radioButtons = locator.all();
        if (radioButtons.isEmpty()) {
            LOGGER.warn("No radio buttons found.");
            return;
        }

        for (Locator radioButton : radioButtons) {
            String label = radioButton.getAttribute("aria-label");
            String value = radioButton.getAttribute("value");

            if ((label != null && label.toLowerCase().contains("no")) ||
                    (value != null && value.equalsIgnoreCase("n"))) {
                if (!(boolean) radioButton.evaluate("el => el.checked")) {
                    radioButton.click();
                    return;
                }
            }
        }
        radioButtons.get(ThreadLocalRandom.current().nextInt(radioButtons.size())).click();
    }


    private static void handleTextInput(Page page, Locator locator) {
        try {
            String name = locator.getAttribute("name").toLowerCase();

            if (name.contains("email")) {
                waitAndEnterText(page, locator, "answer.bot@dvsa.gov.uk");
            } else if (name.contains("password")) {
                List<String> passwords = Files.readAllLines(Paths.get("src/test/resources/PwnedPasswordsTop120.txt"));
                waitAndEnterText(page, locator, getRandomFromList(passwords));
            } else if (name.contains("username")) {
                List<String> usernames = Files.readAllLines(Paths.get("src/test/resources/CommonUsernames.txt"));
                waitAndEnterText(page, locator, getRandomFromList(usernames));
            } else if (name.contains("phone")) {
                waitAndEnterText(page, locator, "07" + ThreadLocalRandom.current().nextInt(100000000, 999999999));
            } else if (name.contains("postcode")) {
                waitAndEnterText(page, locator, "NG2 1AY");
            } else if (name.contains("city")) {
                waitAndEnterText(page, locator, RandomStringUtils.secure().nextAlphabetic(9).toLowerCase());
            } else if (name.contains("code")) {
                waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99999)));
            } else if (name.contains("registration")) {
                waitAndEnterText(page, locator, AppConfig.getString("registration"));
            } else if (name.contains("vin")) {
                waitAndEnterText(page, locator, AppConfig.getString("vin"));
            } else if (name.contains("weight")) {
                waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99)));
            } else {
                waitAndEnterText(page, locator, RandomStringUtils.secure().nextAlphabetic(11).toLowerCase());
            }

        } catch (IOException e) {
            LOGGER.error("Error reading text input data", e);
        }
    }

    private static String getRandomFromList(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
    
    public static List<Locator> getAllClickableButtons(Page page) {
        List<Locator> allButtons = new ArrayList<>();
        
        try {
            String combinedSelector = String.join(", ",
                "button:visible:enabled",
                "input[type='submit']:visible:enabled",
                "input[type='button']:visible:enabled", 
                "input[type='reset']:visible:enabled",
                "a:visible[class*='button']",
                "a:visible[class*='btn']",
                "[role='button']:visible"
            );
            
            allButtons.addAll(page.locator(combinedSelector).all());
            
            String[] essentialButtonTexts = {
                "submit", "continue", "next", "start", "confirm", "ok", "apply", "save"
            };
            
            for (String text : essentialButtonTexts) {
                String caseInsensitiveSelector = String.format(
                    "button:visible:enabled:has-text(/%s/i), a:visible:has-text(/%s/i)", 
                    text, text);
                try {
                    allButtons.addAll(page.locator(caseInsensitiveSelector).all());
                } catch (Exception e) {
                    allButtons.addAll(page.locator("button:visible:enabled:has-text('" + text + "'), a:visible:has-text('" + text + "')").all());
                }
            }
            
            allButtons.addAll(page.locator("div:visible[onclick]:first-of-type, span:visible[onclick]:first-of-type").all());
            
        } catch (Exception e) {
            LOGGER.warn("Error collecting buttons, falling back to basic selector: {}", e.getMessage());
            // Fallback to basic button selector
            allButtons.addAll(page.locator("button:visible:enabled, input[type='submit']:visible:enabled").all());
        }
        
        // Optimized filtering - remove duplicates and invalid elements
        return allButtons.stream()
                .distinct()
                .filter(AnswerBot::isValidClickableButton)
                .limit(10) // Limit to avoid excessive button clicks
                .collect(Collectors.toList());
    }
    

    private static boolean isValidClickableButton(Locator button) {
        try {
            if (!button.isVisible()) return false;
            
            String text = button.textContent().toLowerCase();
            String value = button.getAttribute("value");
            if (value != null) value = value.toLowerCase();
            
            return !text.contains("sign out") &&
                   !text.contains("logout") && 
                   !text.contains("log out") &&
                   !text.contains("remove") && 
                   !text.contains("cookies") && 
                   (value == null || (!value.contains("sign out") && 
                                    !value.contains("logout") && 
                                    !value.contains("remove") &&
                                    !value.contains("cookies"))) &&
                   text.length() <= 100;
        } catch (Exception e) {
            return false;
        }
    }


}