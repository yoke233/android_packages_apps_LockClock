package com.cyanogenmod.lockclock.weather;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.AssetsDatabaseManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Created by yoke on 2016-04-11.
 */
public class WeatherCnProvider implements WeatherProvider {

    private static final String TAG = "WeatherCnProvider";

    private static final String URL_WEATHER =
            "http://wthrcdn.etouch.cn/weather_mini?citykey=%s";

    private Context mContext;

    public WeatherCnProvider(Context context) {
        mContext = context;
        // 初始化，只需要调用一次
        AssetsDatabaseManager.initManager(mContext);
    }

    @Override
    public List<LocationResult> getLocations(String input) {
        // 获取管理对象，因为数据库需要通过管理对象才能够获取
        AssetsDatabaseManager mg = AssetsDatabaseManager.getManager();
        // 通过管理对象获取数据库
        SQLiteDatabase db1 = mg.getDatabase("1123.db");
        // 对数据库进行操作
        Cursor cursor = db1.rawQuery("select * from cityids where name like ? or enname like ?", new String[]{"%" + input + "%", "%" + input + "%"});
        ArrayList<LocationResult> results = new ArrayList<LocationResult>();
        while (cursor.moveToNext()) {
            String id = cursor.getString(0);
            String name = cursor.getString(1);
            String enname = cursor.getString(2);
            String parent = cursor.getString(3);

            LocationResult location = new LocationResult();
            location.id = id;
            location.city = name;
            location.postal = enname;
            location.countryId = id.substring(0, 5);
            location.country = parent;
            results.add(location);
        }
        cursor.close();
        return results;
    }

    @Override
    public WeatherInfo getWeatherInfo(String id, String localizedCityName, boolean metricUnits) {
        String conditionUrl = String.format(URL_WEATHER, id);
        String conditionResponse = HttpRetriever.retrieve2(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }

        Log.v(TAG, "URL = " + conditionUrl + " returning a response of " + conditionResponse);

        try {
            JSONObject conditions = new JSONObject(conditionResponse);
            JSONObject conditionData = conditions.getJSONObject("data");
            JSONObject weather = conditionData.getJSONArray("forecast").getJSONObject(0);
            ArrayList<WeatherInfo.DayForecast> forecasts = parseForecasts(conditionData.getJSONArray("forecast"));
            int speedUnitResId = R.string.weather_kph;

            WeatherInfo w = new WeatherInfo(mContext, id, conditionData.getString("city"),
                    /* condition */ weather.getString("type"),
                    /* conditionCode */ mapConditionIconToCode(weather.getString("type")),
                    /* temperature */ (float) conditionData.getDouble("wendu"),
                    /* tempUnit */ "C",
                    /* humidity */ 0f,
                    /* wind */ 0f,
                    /* windDir */ 0,
                    /* speedUnit */ mContext.getString(speedUnitResId),
                    forecasts,
                    System.currentTimeMillis());

            Log.d(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + id
                    + ")", e);
        }
        return null;
    }


    private ArrayList<WeatherInfo.DayForecast> parseForecasts(JSONArray forecasts) throws JSONException {
        ArrayList<WeatherInfo.DayForecast> result = new ArrayList<WeatherInfo.DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            JSONObject forecast = forecasts.getJSONObject(i);
            WeatherInfo.DayForecast item = new WeatherInfo.DayForecast(
                    /* low */ sanitizeTemperature(forecast.getString("low")),
                    /* high */ sanitizeTemperature(forecast.getString("high")),
                    /* condition */ forecast.getString("type"),
                    /* conditionCode */ mapConditionIconToCode(forecast.getString("type")));
            result.add(item);
        }

        return result;
    }

    private float sanitizeTemperature(String value) {
        value = value.replaceAll("低温 ", "");
        value = value.replaceAll("高温 ", "");
        value = value.replaceAll("℃", "");
        return Float.parseFloat(value);
    }


    private int mapConditionIconToCode(String type) {
        switch (type){
            case "晴":
                return 32;
            case "多云":
                return 28;
            case "阴":
                return 26;
            case "阵雨":
                return 11;
            case "雷阵雨":
                return 45;
            case "雨夹雪":
                return 5;
            case "小雨":
                return 9;
            case "中雨":
                return 11;
            case "大雨":
                return 12;
            case "暴雨":
                return 12;
            case "阵雪":
            case "小雪":
                return 42;
            case "中雪":
                return 41;
            case "大雪":
            case "暴雪":
                return 43;
            case "雾":
                return 20;
            case "霾":
                return 22;
            default:
                return -1;
        }
    }

    @Override
    public WeatherInfo getWeatherInfo(Location location, boolean metricUnits) {
        return null;
    }

    @Override
    public int getNameResourceId() {
        return R.string.weather_source_weathercn;
    }
}
