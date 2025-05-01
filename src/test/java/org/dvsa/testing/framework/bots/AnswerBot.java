package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.typesafe.config.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.dvsa.testing.framework.axe.AXEScanner.scan;
import static org.dvsa.testing.framework.browser.PlayWrightWaits.waitAndEnterText;


public class AnswerBot {
    static List<ElementHandle> inputElements;
    private static final Config config = ConfigFactory.defaultApplication();
    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    public static void formAutoFill(Page page, String url) {
        page.navigate(url);
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
                                waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
                        case "checkbox" -> {
                            boolean isChecked = (boolean) element.evaluate("el => el.checked");
                            if (!isChecked) element.click();
                        }
                        default -> LOGGER.info("Unsupported input type: {}", type);
                    }
                }

                Thread.sleep(4000);
                scan(page);

                List<Locator> buttons = page.locator("button, input[type='button'], input[type='submit'], .button").all();
                if (buttons.isEmpty()) return;

                buttons.get(ThreadLocalRandom.current().nextInt(buttons.size())).click();
                page.reload();
                page.waitForTimeout(1000);

                boolean errorLocator = page.isVisible("#validationBox");
                if (errorLocator) break;

                LOGGER.info("Form submitted successfully.");

                for (Page p : page.context().pages()) {
                    if (p.url().contains("print")) return;
                }

            } catch (PlaywrightException e) {
                LOGGER.info("Encountered a stale element issue, retrying... Attempt {}", ++attempts);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
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
                waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
            } else if (name.contains("code")) {
                waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99999)));
            } else if (name.contains("registration")) {
                waitAndEnterText(page, locator, config.getString("registration"));
            } else if (name.contains("vin")) {
                waitAndEnterText(page, locator, config.getString("vin"));
            } else if (name.contains("weight")) {
                waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99)));
            } else {
                waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
            }

        } catch (IOException e) {
            LOGGER.error("Error reading text input data", e);
        }
    }

    private static String getRandomFromList(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}