package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.typesafe.config.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

        boolean clicked = false;
        Locator locator;

        while (attempts < maxAttempts) {
            try {
                inputElements = page.querySelectorAll("input");

                for (ElementHandle element : inputElements) {
                    if (element == null) continue;

                    String type = element.getAttribute("type");
                    boolean isDisabled = element.isDisabled();
                    boolean isHidden = element.isHidden();

                    if (!isDisabled && !isHidden && type != null) {
                        locator = page.locator("input[name='" + element.getAttribute("name") + "']");

                        switch (type) {
                            case "radio" -> {
                                if (!element.isChecked()) {
                                    List<Locator> radioButtons = locator.all();

                                    if (!radioButtons.isEmpty()) {
                                        for (Locator radioButton : radioButtons) {
                                            String label = radioButton.getAttribute("aria-label");
                                            String value = radioButton.getAttribute("value");

                                            if ((label != null && label.toLowerCase().contains("no")) ||
                                                    (value != null && value.equalsIgnoreCase("n"))) {

                                                if (!radioButton.isChecked()) {
                                                    radioButton.click();
                                                    clicked = true;
                                                    break;
                                                }
                                            }
                                        }
                                        if (!clicked) {
                                            int randomNum = ThreadLocalRandom.current().nextInt(0, radioButtons.size());
                                            radioButtons.get(randomNum).click();
                                            clicked = true;
                                        }
                                    } else {
                                        LOGGER.warn("No radio buttons found.");
                                    }
                                }
                            }
                            case "text" -> {
                                String fieldSnapshot = locator.getAttribute("name");
                                if (fieldSnapshot.toLowerCase().contains("email")) {
                                    waitAndEnterText(page, locator, "testing.crawler@dvsa.gov.uk");
                                } else if (fieldSnapshot.toLowerCase().contains("password")) {
                                    waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                } else if (fieldSnapshot.toLowerCase().contains("username")) {
                                    waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                } else if (fieldSnapshot.toLowerCase().contains("phone")) {
                                    String phoneNumber = "07" + ThreadLocalRandom.current().nextInt(100000000, 999999999);
                                    waitAndEnterText(page, locator, phoneNumber);
                                } else if (fieldSnapshot.toLowerCase().contains("postcode")) {
                                    waitAndEnterText(page, locator, "NG2 1AY");
                                } else if (fieldSnapshot.toLowerCase().contains("city")) {
                                    waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                } else if (fieldSnapshot.toLowerCase().contains("code")) {
                                    waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99999)));
                                } else if (fieldSnapshot.toLowerCase().contains("registration")) {
                                    waitAndEnterText(page, locator, config.getString("registration"));
                                } else if (fieldSnapshot.toLowerCase().contains("vin")) {
                                    waitAndEnterText(page, locator, config.getString("vin"));
                                } else if (fieldSnapshot.toLowerCase().contains("weight")) {
                                    waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99)));
                                } else {
                                    waitAndEnterText(page, locator, RandomStringUtils.randomAlphabetic(9).toLowerCase());
                                }
                            }
                            case "number" -> waitAndEnterText(page, locator, String.valueOf(ThreadLocalRandom.current().nextInt(0, 99999)));
                            case "checkbox" -> {
                                boolean allChecked = true;
                                boolean isChecked = (boolean) element.evaluate("el => el.checked");
                                if (!isChecked) {
                                    allChecked = false;
                                    element.click();
                                }
                            }
                            default -> LOGGER.info("Unsupported input type: {}", type);
                        }
                    }
                }
                Thread.sleep(4000);
                scan(page);

                // Click the submit button after processing all elements
                List<Locator> buttons = page.locator("button, input[type='button'], input[type='submit'], .button").all();

                if (buttons.isEmpty()) {
                    return;
                }

                int randomIndex = ThreadLocalRandom.current().nextInt(buttons.size());
                Locator randomBtn = buttons.get(randomIndex);

                randomBtn.click();
                page.reload();
                page.waitForTimeout(1000);

                LOGGER.info("Form submitted successfully.");

                List<Page> allPages = page.context().pages();
                for (Page p : allPages) {
                    if (p.url().contains("print")) {
                        return;
                    }
                }
            } catch (PlaywrightException e) {
                LOGGER.info("Encountered a stale element issue, retrying... Attempt {}", attempts + 1);
                attempts++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}