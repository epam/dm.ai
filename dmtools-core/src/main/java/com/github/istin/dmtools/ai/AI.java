package com.github.istin.dmtools.ai;

import com.github.istin.dmtools.ai.model.Metadata;
import com.github.istin.dmtools.ai.utils.AIResponseParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public interface AI {

    void setMetadata(Metadata metadata);

    String chat(String message) throws Exception;

    String chat(String model, String message) throws Exception;

    String chat(String model, String message, File imageFile) throws Exception;

    String chat(String model, String message, List<File> files) throws Exception;

    String chat(String model, Message... messages) throws Exception;

    String chat(Message... messages) throws Exception;

    class Utils {

        public static Boolean chatAsBoolean(AI ai, String model, String message) throws Exception {
            return Boolean.parseBoolean(ai.chat(model, message));
        }

        public static JSONArray chatAsJSONArray(AI ai, String model, String message) throws Exception {
            return AIResponseParser.parseResponseAsJSONArray(ai.chat(model, message, (File) null));
        }

        public static JSONArray chatAsJSONArray(AI ai, String message) throws Exception {
            return chatAsJSONArray(ai, null, message);
        }

        public static JSONObject chatAsJSONObject(AI ai, String model, String message) throws Exception {
            return AIResponseParser.parseResponseAsJSONObject(ai.chat(model, message, (File) null));
        }

        public static JSONObject chatAsJSONObject(AI ai, String message) throws Exception {
            return chatAsJSONObject(ai, null, message);
        }

        public static boolean chatAsBoolean(AI ai, String message) throws Exception {
            return chatAsBoolean(ai, null, message);
        }

    }

}
