package de.baumann.browser.objects;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class CustomRedirectsHelper {

    public final static String CUSTOM_REDIRECTS_KEY = "customRedirects";

    public static ArrayList<CustomRedirect> getRedirects(SharedPreferences preferences) throws JSONException {
        ArrayList<CustomRedirect> redirects = new ArrayList<>();
        String redirectsPref = preferences.getString(CUSTOM_REDIRECTS_KEY, "[]");

        JSONArray array = new JSONArray(redirectsPref);
        for (int i = 0; i < array.length(); i++) {
            JSONObject redirect = array.getJSONObject(i);
            String source = redirect.getString("source");
            String target = redirect.getString("target");
            redirects.add(new CustomRedirect(source, target));
        }
        return redirects;
    }

    public static void saveRedirects(Context context, ArrayList<CustomRedirect> redirects) throws JSONException {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        JSONArray array = new JSONArray();
        for (int i = 0; i < redirects.size(); i++) {
            CustomRedirect redirect = redirects.get(i);
            JSONObject object = new JSONObject();
            object.put("source", redirect.getSource());
            object.put("target", redirect.getTarget());
            array.put(object);
        }

        preferences.edit().putString(CUSTOM_REDIRECTS_KEY, array.toString()).apply();
    }
}
