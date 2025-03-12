package org.dvsa.testing.lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;

public class UrlValidator {

    private static final Logger LOGGER = LogManager.getLogger(UrlValidator.class);

    public static boolean isURLValid(String url) {
        try {
            if (url == null || url.trim().isEmpty()) {
                return false;
            }

            // Validate URL format
            URL obj = new URL(url);
            obj.toURI();

            //Check if the URL is reachable
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            //Only 200 responses
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return false;
        }
    }
}

