package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class JSONResourceReader  {

    private static final Logger logger = LogManager.getLogger(JSONResourceReader.class);

    private final String file;

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    private JSONObject jsonObject;

    private static JSONResourceReader instance;

    private static Map<String, JSONResourceReader> jsonConfigs = new HashMap<>();


    JSONResourceReader(String file) {
        this.file = file;
        readJSON();
    }

    public static JSONResourceReader getInstance(String file) {
        return jsonConfigs.computeIfAbsent(file, JSONResourceReader::new);
    }

    void readJSON() {
        InputStream input = null;
        try {
            input = getClass().getResourceAsStream("/" + file);
            if (input != null) {
                String source = convertInputStreamToString(input);
                jsonObject = new JSONObject(source);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Property file not found");
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                logger.error("Unexpected error", e);
            }
        }
    }

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    static String convertInputStreamToString(InputStream is) throws IOException {

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        return result.toString("UTF-8");
    }
}
