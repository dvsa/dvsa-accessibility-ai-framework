package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.dvsa.testing.framework.config.AppConfig;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.dvsa.testing.framework.axe.AXEScanner.scan;
import static org.dvsa.testing.framework.browser.PlayWrightWaits.waitAndEnterText;


public class AnswerBot {
    static List<ElementHandle> inputElements;

    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    public static void formAutoFill(Page page, String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.LOAD);
        inputElements = page.querySelectorAll("input");

        int attempts = 0;
        int maxAttempts = 10;

        Locator locator;

        while (attempts < maxAttempts) {
            try {
                inputElements = page.querySelectorAll("input");

                for (ElementHandle element : inputElements) {
                    if (element == null) continue;

                    String type = element.getAttribute("type");
                    boolean isDisabled = element.isDisabled();
                    boolean isHidden = element.isHidden();

                    if (type == null || isDisabled || isHidden) continue;

                    locator = page.locator("input[name='" + element.getAttribute("name") + "']");

                    switch (type) {
                        case "radio" -> handleRadioButton(locator, element);
                        case "text", "password" -> handleTextInput(page, locator);
                        case "number" ->
                                waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 9999)));
                        case "search" ->
                                waitAndEnterText(page, locator, "Registration plate");
                        case "checkbox" -> {
                            boolean isChecked = (boolean) element.evaluate("el => el.checked");
                            if (!isChecked) element.click();
                        }
                        default -> LOGGER.info("Unsupported input type: {}", type);
                    }
                }

                page.waitForTimeout(250);
                scan(page);
                List<Locator> buttons = getAllClickableButtons(page);
                
                buttons = buttons.stream()
                        .filter(button -> {
                            try {
                                String text = button.textContent().toLowerCase();
                                String value = button.getAttribute("value");
                                if (value != null) value = value.toLowerCase();
                                return !text.contains("sign out") && !text.contains("logout") && 
                                       (value == null || (!value.contains("sign out") && !value.contains("logout")));
                            } catch (Exception e) {
                                return true; // Include button if we can't check its text
                            }
                        })
                        .toList();
                        
                if (buttons.isEmpty()) return;

                Locator selectedButton = buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));

                boolean isClickable = selectedButton.evaluate("el => el.tagName.toLowerCase() === 'a' || !el.disabled").toString().equals("true");
                if (isClickable) {
                    try {
                        selectedButton.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(2000));
                        
                        selectedButton.click();
                        page.waitForTimeout(300);
                        scan(page);
                    } catch (Exception e) {
                        LOGGER.warn("Button click failed: {}", e.getMessage());
                        // Try alternative click method
                        try {
                            selectedButton.dispatchEvent("click");
                            page.waitForTimeout(300);
                            scan(page);
                        } catch (Exception e2) {
                            LOGGER.error("Both click methods failed: {}", e2.getMessage());
                        }
                    }
                }

                boolean errorLocator = page.isVisible("#validationBox");
                if (errorLocator) break;

                LOGGER.info("Form submitted successfully.");

                for (Page p : page.context().pages()) {
                    if (p.url().contains("print") || p.url().contains("testing-advice?"))
                        return;
                }

            } catch (PlaywrightException e) {
                LOGGER.info("Playwright exception encountered, retrying... Attempt {}: {}", ++attempts, e.getMessage());
                if (e.getMessage().contains("stale") || e.getMessage().contains("detached")) {
                    page.reload();
                    page.waitForTimeout(500);
                } else {
                    page.waitForTimeout(300);
                }
            }
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
                   (value == null || (!value.contains("sign out") && 
                                    !value.contains("logout") && 
                                    !value.contains("remove"))) &&
                   text.length() <= 100;
        } catch (Exception e) {
            return false;
        }
    }
}