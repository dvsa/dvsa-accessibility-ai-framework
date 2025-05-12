package org.dvsa.testing.framework.jsoup;

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

            URL obj = new URL(url);
            obj.toURI();

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return false;
        }
    }
}