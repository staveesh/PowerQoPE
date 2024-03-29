/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.ac.uct.cs.powerqope.util;

import android.content.Context;
import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import za.ac.uct.cs.powerqope.Logger;
import za.ac.uct.cs.powerqope.MeasurementDesc;
import za.ac.uct.cs.powerqope.MeasurementTask;

/**
 * Utility class that use the gson library to provide bidirectional conversion between
 * measurement objects (descriptions, tasks, and results, etc.) and JSON objects.
 * New types of MeasurementDesc should be registered in the static HashMap initialization
 * section.
 */
@SuppressWarnings("rawtypes")
public class MeasurementJsonConvertor {
    /* 1. Automatically perform bidirectional translations for fields in java 'lowerCaseCamel' style
     * to JSON 'lower_case_with_underscores' style.
     * 2. Serialize and de-serialize UTC format date string
     * 3. It also serializes all null fields to 'null'
     */
    public static Gson gson = new GsonBuilder().serializeNulls().
            registerTypeAdapter(Date.class, new DateTypeConverter()).
            setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).create();
    private static final DateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static MeasurementTask makeMeasurementTaskFromJson(JSONObject json, Context context)
            throws IllegalArgumentException {
        try {
            String type = String.valueOf(json.getString("type"));
            Class taskClass = MeasurementTask.getTaskClassForMeasurement(type);
            Method getDescMethod = taskClass.getMethod("getDescClass");
            // The getDescClassForMeasurement() is static and takes no arguments
            Class descClass = (Class) getDescMethod.invoke(null, (Object[]) null);
            MeasurementDesc measurementDesc = (MeasurementDesc) gson.fromJson(json.toString(), descClass);
            Object[] cstParams = {measurementDesc, context};
            Constructor<MeasurementTask> constructor =
                    taskClass.getConstructor(MeasurementDesc.class, Context.class);
            return constructor.newInstance(cstParams);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        } catch (SecurityException e) {
            Logger.w(e.getMessage());
            throw new IllegalArgumentException(e);
        } catch (NoSuchMethodException e) {
            Logger.w(e.getMessage());
            throw new IllegalArgumentException(e);
        } catch (IllegalArgumentException e) {
            Logger.w(e.getMessage());
            throw new IllegalArgumentException(e);
        } catch (InstantiationException e) {
            Logger.w(e.getMessage());
            throw new IllegalArgumentException(e);
        } catch (IllegalAccessException e) {
            Logger.w(e.getMessage());
            throw new IllegalArgumentException(e);
        } catch (InvocationTargetException e) {
            Logger.w(e.toString());
            throw new IllegalArgumentException(e);
        }
    }

    public static JSONObject encodeToJson(Object obj) throws JSONException {
        String str = gson.toJson(obj);
        return new JSONObject(str);
    }

    public static String toJsonString(Object obj) {
        return gson.toJson(obj);
    }

    public static Gson getGsonInstance() {
        return gson;
    }

    private static class DateTypeConverter implements JsonSerializer<Date>,
            JsonDeserializer<Date> {
        @Override
        public JsonElement serialize(Date src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(formatDate(src));
        }

        @Override
        public Date deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return parseDate(json.getAsString());
            } catch (NumberFormatException e) {
                throw new JsonParseException("Cannot convert time string: " + json.toString());
            } catch (IllegalArgumentException e) {
                Logger.e("Cannot convert time string:" + json.toString());
                throw new JsonParseException("Cannot convert time string: " + json.toString());
            } catch (ParseException e) {
                throw new JsonParseException("Cannot convert UTC time string: " + json.toString());
            }
        }
    }

    private static Date parseDate(String dateString) throws ParseException {
        Date parsedDate = dateFormat.parse(dateString);
        if(parsedDate == null)
            Log.i("Measurements", "Parsed date is null");
        else
            Log.i("Measurements", parsedDate.toString());
        return parsedDate;
    }

    private static String formatDate(Date date) {
        return dateFormat.format(date);
    }
}
