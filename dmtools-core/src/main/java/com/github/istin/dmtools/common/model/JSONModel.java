// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.common.model;

import com.github.istin.dmtools.common.utils.Log;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JSONModel {

    private static final Logger logger = LogManager.getLogger(JSONModel.class);

    private static final String TAG = JSONModel.class.getSimpleName();
    /**
     * The underlying JSON object.
     */
    private JSONObject jo;

    /**
     * Instantiates a new base model.
     */
    public JSONModel() {
        jo = new JSONObject();
    }

    /**
     * Instantiates a new base model with underlying JSON object as String.
     *
     * @param json the json
     * @throws org.json.JSONException the jSON exception
     */
    public JSONModel(final String json) throws JSONException {
        try {
            jo = new JSONObject(json);
        } catch (Exception e) {
            logger.error("json object is not correct {}", json);
            throw e;
        }
    }

    /**
     * Instantiates a new base model with underlying JSON object.
     *
     * @param json the json object
     */
    public JSONModel(final JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("JSONObject argument is null");
        }
        jo = json;
    }

    /**
     * Sets the value for key.
     *
     * @param key   the key
     * @param value the value
     */
    public final void set(final String key, final Object value) {
        try {
            synchronized (jo) {
                if (value == null) {
                    jo.remove(key);
                } else {
                    jo.put(key, value);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
    }

    public final void setArray(final String key, final String... values) {
        try {
            synchronized (jo) {
                if (values == null) {
                    jo.remove(key);
                } else {
                    JSONArray array = new JSONArray();
                    for (String value : values) {
                        array.put(value);
                    }
                    jo.put(key, array);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
    }

    protected final void setModel(final String key, final JSONModel model) {
        synchronized (jo) {
            if (model == null) {
                jo.remove(key);
            } else {
                try {
                    jo.put(key, model.getJSONObject());
                } catch (JSONException e) {
                    Log.e(TAG, e);
                }
            }
        }
    }

    /**
     * Gets the value for key.
     *
     * @param key the key
     * @return the object
     */
    protected final Object get(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.get(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    /**
     * Gets the String value.
     *
     * @param key the key
     * @return the string
     */
    public final String getString(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getString(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    /**
     * Gets the inner string from nested JSON object.
     *
     * @param jkey the nested JSON object jkey
     * @param key  the String key
     * @return the inner string
     */
    protected final String getInnerString(final String jkey, final String key) {
        try {
            JSONObject ijo = getJSONObject(jkey);
            if (ijo == null) {
                return null;
            }
            return ijo.getString(key);
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;

    }

    /**
     * Gets the Integer value.
     *
     * @param key the key
     * @return the int
     */
    public final int getInt(final String key) {
        return jo.optInt(key);
    }

    /**
     * Gets the Double value.
     *
     * @param key the key
     * @return the double
     */
    protected final Double getDouble(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getDouble(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    /**
     * Gets the Boolean value.
     *
     * @param key the key
     * @return the boolean, default Boolean.FALSE
     */
    public final Boolean getBoolean(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getBoolean(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return Boolean.FALSE;
    }

    /**
     * Gets the Long value.
     *
     * @param key the key
     * @return the long
     */
    protected final Long getLong(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getLong(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    /**
     * Gets the JSON object value.
     *
     * @param key the key
     * @return the jSON object
     */
    public final JSONObject getJSONObject(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getJSONObject(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    /**
     * Gets the JSONArray value.
     *
     * @param key the key
     * @return the JSONArray
     */
    public final JSONArray getJSONArray(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getJSONArray(key);
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    public  <Model extends JSONModel> Model getModel(Class<Model> clazz, final String key) {
        JSONObject jsonObject = getJSONObject(key);
        if (jsonObject == null) {
            return null;
        }
        Model model = null;
        try {
            model = clazz.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        model.setJO(jsonObject);
        return model;
    }

    public  <ReturnType, Model extends JSONModel> List<ReturnType> getModels(Class<Model> clazz, final String key) {
        JSONArray jsonArray = getJSONArray(key);
        return convertToModels(clazz, jsonArray);
    }

    public static  <ReturnType, Model extends JSONModel> List<ReturnType> convertToModels(Class<Model> clazz, JSONArray jsonArray) {
        if (jsonArray != null && jsonArray.length() > 0) {
            List<ReturnType> models = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                Model model = null;
                try {
                    model = clazz.newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                try {
                    model.setJO(jsonArray.getJSONObject(i));
                } catch (JSONException e) {
                    throw new IllegalStateException(String.valueOf(jsonArray.opt(i)), e);
                }
                models.add((ReturnType) model);
            }
            return models;

        }
        return Collections.emptyList();
    }

    /**
     * Gets the JSONArray value size.
     *
     * @param key the key
     * @return the jSON array size
     */
    protected final Integer getJSONArraySize(final String key) {
        try {
            if (!jo.isNull(key)) {
                return jo.getJSONArray(key).length();
            }
        } catch (JSONException e) {
            Log.e(TAG, e);
        }
        return null;
    }

    /**
     * Gets the underlying JSON object.
     *
     * @return the jSON object
     */
    public final JSONObject getJSONObject() {
        return jo;
    }


    public void setJO(JSONObject object) {
        this.jo = object;
    }

    @Override
    public String toString() {
        if (jo != null) {
            return jo.toString();
        }
        return super.toString();
    }

    public void copyFields(JSONModel to, String... keys) {
        for (String key : keys) {
            to.set(key, get(key));
        }
    }

    public String[] getStringArray(String key) {
        JSONArray jsonArray = getJSONArray(key);
        if (jsonArray != null) {
            String[] stringArray = new String[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    stringArray[i] = jsonArray.getString(i);
                } catch (JSONException e) {
                    // Handle the exception as per your requirement
                    e.printStackTrace();
                }
            }
            return stringArray;
        }
        return null;
    }

}
