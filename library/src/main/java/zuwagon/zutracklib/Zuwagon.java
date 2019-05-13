package zuwagon.zutracklib;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static zuwagon.zutracklib.Constants.LAST_LOCATION;
import static zuwagon.zutracklib.Constants.MIN_DISTANCE;
import static zuwagon.zutracklib.Constants.ONGPS;
import static zuwagon.zutracklib.Constants.START_STOP;
import static zuwagon.zutracklib.Constants.TAG;
import static zuwagon.zutracklib.ZWStatusCallback.BASE_URL;
import static zuwagon.zutracklib.ZWStatusCallback.CALL_API;

/**
 * Entry library class. Purposed to configure and control location tracker.
 * All methods are static.
 */
public class Zuwagon {

    private static SharedPreferences _config;
    static Handler _uiHandler = new Handler(Looper.getMainLooper());

    static boolean _needServiceStarted = false;
    static int _notificationSmallIconRes = R.drawable.ic_service_notify;
    static String _notificationChannelTitle = null;
    static String _notificationTitle = null;
    static String _notificationText = null;
    static String _notificationTicker = null;
    static String _riderId = null;
    static String _apiKey = null;

    static int _rationaleTextRes = R.string.default_rationale_access_fine_location;
    static int _rationalePositiveButtonRes = R.string.default_rationale_positive_button;

    static boolean _needForegroundService = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);

    static ZWLocationService _currentServiceInstance = null;

    private static final ArrayList<ZWStatusCallback> statusCallbacks = new ArrayList<>();
    private static int lastStatus = ZWStatus.NONE;

    static final ArrayList<ZWProcessLocationCallback> processLocationCallbacks = new ArrayList<>();

    private static Thread.UncaughtExceptionHandler defaultGlobalExceptionHandler;
    private boolean isGpsenabled = false;


    /**
     * Initial framework configuration.
     *
     * @param context                    Application or activity context.
     * @param notificationSmallIconRes   Resource Id of small (24x24dp) notification icon. It will appear in foreground service notification and in rationale dialog.
     * @param notificationChannelTitle   Notification channel title (Oreo and later).
     * @param notificationTitle          Notification title.
     * @param notificationText           Notification text.
     * @param notificationTicker         Notification ticker. Can be null.
     * @param rationaleTextRes           String resource id of location permissions rationale text.
     * @param rationalePositiveButtonRes String resource of 'OK/GOT IT' button.
     */

    public static void configure(final Context context,
                                 String riderId,
                                 String apiKey,
                                 @DrawableRes int notificationSmallIconRes,
                                 String notificationChannelTitle,
                                 String notificationTitle,
                                 String notificationText,
                                 String notificationTicker,
                                 @StringRes int rationaleTextRes,
                                 @StringRes int rationalePositiveButtonRes
    ) {
        _config = PreferenceManager.getDefaultSharedPreferences(context);
        _riderId = riderId;
        _apiKey = "Bearer " + apiKey;
        _notificationSmallIconRes = notificationSmallIconRes != 0 ? notificationSmallIconRes : R.drawable.ic_service_notify;
        _notificationChannelTitle = notificationChannelTitle;
        _notificationTitle = notificationTitle;
        _notificationText = notificationText;
        _notificationTicker = notificationTicker;
        _rationaleTextRes = rationaleTextRes != 0 ? rationaleTextRes : R.string.default_rationale_access_fine_location;
        _rationalePositiveButtonRes = rationalePositiveButtonRes != 0 ? rationalePositiveButtonRes : R.string.default_rationale_positive_button;

        _needServiceStarted = config().getBoolean(Constants.CONFIG.NEED_SERVICE_STARTED, false);

        defaultGlobalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();


        // Tracker self-stopping on uncaught exception and calls default exception handler
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.d(TAG, "Stopping tracking on application exception.");
                if (isTracking()) stopTrackingService(context);
                defaultGlobalExceptionHandler.uncaughtException(t, e);
            }
        });
    }

    /**
     * Start tracking command. Launches tracking flow.
     *
     * @param context Application or activity context.
     */
    static void startTrackingService(Context context) {
        setNeedServiceStarted(true);
        ZWLocationService.start(context);
        ZWAlarmReceiver.setupAlarm(context, Constants.RECREATE_SERVICE_ON_DESTROY_DELAY_MS);
        ZWSocket.connectToServer();
    }

    /**
     * Stop tracking command. Interrupts tracking flow.
     *
     * @param context Application or activity context.
     */

    public static final void stopTrackingService(Context context) {
        setNeedServiceStarted(false);
        ZWAlarmReceiver.clearAlarm(context);
        ZWSocket.disconnectFromServer();

    }

    /**
     * Check tracking enabled and active.
     *
     * @return true if tracking enabled and active.
     */
    public static final boolean isTracking() {
        return _currentServiceInstance != null && _needServiceStarted;
    }

    ///
    /// Status processing
    ///

    /**
     * Adds status receiver callback. Multiple callbacks can be added.
     *
     * @param callback         {@link ZWStatusCallback ZWStatusCallback} implementation
     * @param returnLastStatus true if need to return last status to current callback
     */
    public static final void addStatusCallback(final ZWStatusCallback callback, boolean returnLastStatus) {
        if (callback != null) {
            statusCallbacks.add(callback);
            if (returnLastStatus && lastStatus != ZWStatus.NONE) runStatusCallback(callback);
        }
    }

    /**
     * Removes status receiver callback from callbacks list.
     *
     * @param callback {@link ZWStatusCallback ZWStatusCallback} reference to remove
     */
    public static final void removeStatusCallback(ZWStatusCallback callback) {
        statusCallbacks.remove(callback);
    }

    ///
    /// Received location processing
    ///

    /**
     * Adds location processor. Multiple callbacks can be added.
     * When new location data received from Fused Location Provider, all callbacks are triggered.
     *
     * @param processLocationCallback {@link ZWProcessLocationCallback ZWProcessLocationCallback} implementation
     */
    public static final void addLocationProcessor(ZWProcessLocationCallback processLocationCallback) {
        if (!processLocationCallbacks.contains(processLocationCallback)) {
            processLocationCallbacks.add(processLocationCallback);
        }
    }

   /* public static void getFastLocation(Context context) {
        SingleShotLocationProvider.requestSingleUpdate(context, new SingleShotLocationProvider.LocationCallback() {
            @Override
            public void onNewLocationAvailable(SingleShotLocationProvider.GPSCoordinates location) {
                Log.e("getFastLocation", "getFastLocation  location " + location.latitude);
                Log.e("getFastLocation", "getFastLocation  location " + location.longitude);
            }
        });
    }
*/

    /**
     * Removes location processor from list.
     *
     * @param processLocationCallback {@link ZWProcessLocationCallback ZWProcessLocationCallback} reference to remove
     */
    public static final void removeLocationProcessor(ZWProcessLocationCallback processLocationCallback) {
        processLocationCallbacks.remove(processLocationCallback);
    }

    ///
    /// Getting current location
    ///

    /**
     * Getting current location from Fused Location Provider.
     * Permission checking will be performed if need.
     * <p>
     * The location object may be null in the following situations:
     * <p>
     * Location is turned off in the device settings. The result could be null even if the last location
     * was previously retrieved because disabling location also clears the cache.
     * <p>
     * The device never recorded its location, which could be the case of a new device or a device that has
     * been restored to factory settings.
     * <p>
     * Google Play services on the device has restarted, and there is no active Fused Location Provider
     * client that has requested location after the services restarted. To avoid this situation you can
     * create a new client and request location updates yourself.
     *
     * @param callback Receives location data and call status
     */
    public static final void instantLocation(final Context context, final ZWInstantLocationCallback callback) {

        // Checking location permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            callback.onResult(ZWInstantLocationCallback.PERMISSION_REQUEST_NEED, null);
        } else {
            final FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(context);
            client.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    try {
                        if (location != null) {
                            callback.onResult(ZWInstantLocationCallback.OK, location);
                        } else {
                            callback.onResult(ZWInstantLocationCallback.LOCATION_NOT_AWAILABLE, null);
                        }
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    ///
    /// Internal methods.
    ///

    protected static final SharedPreferences config() {
        return _config;
    }

    static final void setNeedServiceStarted(boolean value) {
        _needServiceStarted = value;
        config().edit().putBoolean(Constants.CONFIG.NEED_SERVICE_STARTED, _needServiceStarted).commit();
    }

    static final void postStatus(final int code) {
        lastStatus = code;
        for (ZWStatusCallback callback : statusCallbacks) runStatusCallback(callback);
    }

    static final void runStatusCallback(final ZWStatusCallback callback) {
        if (callback != null) {
            _uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        callback.onStatus(lastStatus);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    private static ZWHttpCallback zwHttpCallback2;

    public static void setInterface(ZWHttpCallback zwHttpCallback) {
        zwHttpCallback2 = zwHttpCallback;
    }

    public static String StartTracking(Context context, String group_ID, ArrayList order_list) {
        try {
            if (_needServiceStarted) {
                Log.e("StartTracking", "if    ");
                return "Tracking also started";
            } else {
                Log.e("StartTracking", "else   ");

                StartTracking_http(context, group_ID, order_list);

                return "Tracking started";
            }
        } catch (Exception e) {
            Log.e("StartTracking", "exception   ");
            return "System error";
        }
    }

    private static void StartTracking_http(final Context context, final String group_ID, final ArrayList order_list) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(context, ZWResolutionActivity.class);
            intent.putExtra("option", Constants.RESOLUTION_OPTION_PERMISSIONS);
            intent.putExtra(CALL_API, true);
//            Log.e("RESOLUTION_ON_PESIONS", "zwHttpCallback <> " + zwHttpCallback);
//            intent.putExtra(ZWSTATUSCALLBACK, zwHttpCallback);
            intent.putExtra("START_STOP", "START");
            intent.putExtra("Group_ID", group_ID);
            intent.putExtra("ORDER_LIST", order_list);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            enableGPS(context, group_ID, "START");
        }
    }

    private static Location getLocation(SingleShotLocationProvider.GPSCoordinates location) {
        if (location.longitude != 0 && location.latitude != 0) {
            Location targetLocation = new Location("");
            targetLocation.setLatitude(location.latitude);
            targetLocation.setLongitude(location.longitude);
            return targetLocation;
        } else {
            return null;
        }
    }

    private static void callStartAPI(final Context context, Location location, String G_id, ArrayList order_list) {

        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject object = new JSONObject();
        try {
//            object.put("rider_id", "");
            object.put("rider_id", _riderId);
            object.put("trip", "START");
            object.put("group_id", G_id);

            JSONObject loc_obj = new JSONObject();
            loc_obj.put("lat", location.getLatitude());
            loc_obj.put("lon", location.getLongitude());
            object.put("loc", loc_obj);
            JSONArray orderJSONArrray = new JSONArray();
//            for(Order orde : order_list) {
//                //necessary code here
//            }
            for (int i=0; i < order_list.size(); i++) {
                orderJSONArrray.put((Order)(order_list.get(i)));
            }
            object.put("order_list", orderJSONArrray);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, BASE_URL + "/order", object, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.e("onResponse", "onResponse   " + response);
                startTrackingService(context);
                zwHttpCallback2.HttpResponseMsg("START", response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("onErrorResponse", "onErrorResponse   " + error);
                if (error.networkResponse.data != null) {
                    try {
                        String body = new String(error.networkResponse.data, "UTF-8");
                        zwHttpCallback2.HttpErrorMsg("START", body);
                        Log.e("onErrorResponse", "onErrorResponse  body " + body);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }) {

            /**
             * Passing some request headers
             * */

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<>();
                try {
                    headers.put("Authorization", _apiKey);
                    headers.put("Content-Type", "application/json");
                    // headers.put("Content-Type", "application/json");
                    headers.put("source", "x-order-tracking");
                    return headers;
                } catch (Exception e) {
                    return null;
                }
            }
        };
        queue.add(request);
    }


    public static void StopTracking(final Context context, final String G_id) {
        setNeedServiceStarted(false);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(context, ZWResolutionActivity.class);
            intent.putExtra("option", Constants.RESOLUTION_OPTION_PERMISSIONS);
            intent.putExtra(CALL_API, true);
//            intent.putExtra(ZWSTATUSCALLBACK, zwHttpCallback);
            intent.putExtra("START_STOP", "END");
            intent.putExtra("Group_ID", G_id);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            enableGPS(context, G_id, "STOP");
        }
    }

    private static void callStopAPI(final Context context, Location location, String G_id) {
        Log.e("---", "-------------------------------------");
        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject object = new JSONObject();
        try {
//            object.put("rider_id", "");
            object.put("rider_id", _riderId);
            object.put("trip", "END");
            object.put("group_id", G_id);
            JSONObject loc_obj = new JSONObject();
            loc_obj.put("lat", location.getLatitude());
            loc_obj.put("lon", location.getLongitude());
            object.put("loc", loc_obj);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, BASE_URL + "/order", object, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.e("onResponse", "onResponse   " + response);
                stopTrackingService(context);
                zwHttpCallback2.HttpResponseMsg("END", response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("onErrorResponse", "onErrorResponse   " + error);
                String statusCode = String.valueOf(error.networkResponse.statusCode);
                //get response body and parse with appropriate encoding
                if (error.networkResponse.data != null) {
                    try {
                        String body = new String(error.networkResponse.data, "UTF-8");
                        zwHttpCallback2.HttpErrorMsg("END", body);
                        Log.e("onErrorResponse", "onErrorResponse  body " + body);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }) {
            /**
             * Passing some request headers
             * */
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<>();
                try {
                    headers.put("Authorization", _apiKey);
                    headers.put("Content-Type", "application/json");
                    // headers.put("Content-Type", "application/json");
                    headers.put("source", "x-order-tracking");
                    return headers;
                } catch (Exception e) {
                    return null;
                }
            }
        };
        queue.add(request);
    }

    public static void PickUp_order(final Context context, final String group_id, final String order_id) {
        Log.e("PickUp_order", "PickUp_order>>  ");

        SingleShotLocationProvider.requestSingleUpdate(context, new SingleShotLocationProvider.LocationCallback() {
            @Override
            public void onNewLocationAvailable(SingleShotLocationProvider.GPSCoordinates location) {
                Log.e("getFastLocation", "getFastLocation  location " + location.latitude);
                Log.e("getFastLocation", "getFastLocation  location " + location.longitude);
                Location loc = new Location("");
                loc = getLocation(location);
                if (loc != null) {


                    RequestQueue mRequestQueue = Volley.newRequestQueue(context);
                    JSONObject param = new JSONObject();
                    try {
                        JSONObject loc_obj = new JSONObject();
                        loc_obj.put("lat", loc.getLatitude());
                        loc_obj.put("lon", loc.getLongitude());
                        param.put("group_id", group_id);
                        param.put("rider_id", _riderId);
                        param.put("order_id", order_id);
                        param.put("type", "PICKUP");
                        param.put("loc", loc_obj);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                            Request.Method.POST, BASE_URL + "/order/delivery", param, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            zwHttpCallback2.HttpResponseMsg("PICKUP", response);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e("onErrorResponse", "onErrorResponse   " + error);
                            if (error.networkResponse.data != null) {
                                try {
                                    String body = new String(error.networkResponse.data, "UTF-8");
                                    zwHttpCallback2.HttpErrorMsg("PICKUP", body);
                                    Log.e("onErrorResponse", "onErrorResponse  body " + body);
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }) {

                        /**
                         * Passing some request headers
                         * */
                        @Override
                        public Map<String, String> getHeaders() throws AuthFailureError {
                            HashMap<String, String> headers = new HashMap<String, String>();
                            try {
                                headers.put("Authorization", _apiKey);
                                headers.put("Content-Type", "application/json");
                                headers.put("source", "x-order-tracking");
                                return headers;
                            } catch (Exception e) {
                                return null;
                            }
                        }

                    };

                    mRequestQueue.add(jsonObjReq);


                } else {
                    zwHttpCallback2.HttpErrorMsg("START", "Location not available");
                }
            }
        });

    }


    public static void Drop_order(final Context context,final String group_id,final String order_id) {
        Log.e("Drop_order", "Drop_order>>  " );

        SingleShotLocationProvider.requestSingleUpdate(context, new SingleShotLocationProvider.LocationCallback() {
            @Override
            public void onNewLocationAvailable(SingleShotLocationProvider.GPSCoordinates location) {
                Log.e("getFastLocation", "getFastLocation  location " + location.latitude);
                Log.e("getFastLocation", "getFastLocation  location " + location.longitude);

                Location loc = new Location("");
                loc = getLocation(location);
                if (loc != null) {

                    RequestQueue mRequestQueue = Volley.newRequestQueue(context);
                    JSONObject param = new JSONObject();
                    try {
                        JSONObject loc_obj = new JSONObject();
                        loc_obj.put("lat", loc.getLatitude());
                        loc_obj.put("lon", loc.getLongitude());
                        param.put("group_id", group_id);
                        param.put("rider_id", _riderId);
                        param.put("order_id", order_id);
                        param.put("type", "DROP");
                        param.put("loc", loc_obj);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                            Request.Method.POST, BASE_URL + "/order/delivery", param, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            zwHttpCallback2.HttpResponseMsg("DROP", response);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e("onErrorResponse", "onErrorResponse   " + error);
                            if (error.networkResponse.data != null) {
                                try {
                                    String body = new String(error.networkResponse.data, "UTF-8");
                                    zwHttpCallback2.HttpErrorMsg("DROP", body);
                                    Log.e("onErrorResponse", "onErrorResponse  body " + body);
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }) {

                        /**
                         * Passing some request headers
                         * */
                        @Override
                        public Map<String, String> getHeaders() throws AuthFailureError {
                            HashMap<String, String> headers = new HashMap<String, String>();
                            try {
                                headers.put("Authorization", _apiKey);
                                headers.put("Content-Type", "application/json");
                                headers.put("source", "x-order-tracking");
                                return headers;
                            } catch (Exception e) {
                                return null;
                            }
                        }

                    };

                    mRequestQueue.add(jsonObjReq);


                } else {
                    zwHttpCallback2.HttpErrorMsg("START", "Location not available");
                }
            }
        });

    }

    public static void enableGPS(final Context context, final String group_ID, final String start_stop) {

        final LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(Constants.DEFAULT_LOCATION_UPDATE_INTERVAL_MS);
        locationRequest.setFastestInterval(Constants.DEFAULT_LOCATION_UPDATE_FASTEST_INTERVAL_MS);
        locationRequest.setSmallestDisplacement(MIN_DISTANCE);
        locationRequest.setPriority(Constants.DEFAULT_LOCATION_PRIORITY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(context);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                instantLocation(context, new ZWInstantLocationCallback() {
                    @Override
                    public void onResult(int result, Location location) {
                        switch (result) {
                            case ZWInstantLocationCallback.OK:
                                Log.e("StartTracking_http", "StartTracking_http " + location.toString());
                                if (start_stop.equalsIgnoreCase("START")) {
                                    callStartAPI(context, location, group_ID);
                                } else {
                                    callStopAPI(context, location, group_ID);
                                }

                                break;
                            case ZWInstantLocationCallback.LOCATION_NOT_AWAILABLE:
                                if (start_stop.equalsIgnoreCase("STOP")) {
                                    String s = config().getString(LAST_LOCATION, null);
                                    if (s != null) {
                                        String[] a = s.split(",");
                                        double lat = Double.parseDouble(a[0]);
                                        double log = Double.parseDouble(a[1]);
                                        Location location1 = new Location("");
                                        location1.setLatitude(lat);
                                        location1.setLongitude(log);
                                        callStopAPI(context, location1, group_ID);
                                    }
                                }
                                break;
                            case ZWInstantLocationCallback.PERMISSION_REQUEST_NEED:
                                zwHttpCallback2.HttpErrorMsg("START", "Location not available");

                                break;
                        }
                    }
                });
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                PendingIntent pendingIntent = ((ResolvableApiException) e).getResolution();
                Intent intent = new Intent(context, ZWResolutionActivity.class);
                intent.putExtra("option", Constants.RESOLUTION_OPTION_HARDWARE);
                intent.putExtra("Group_ID", group_ID);
                intent.putExtra(ONGPS, true);
                intent.putExtra(START_STOP, start_stop);
                intent.putExtra("resolution", pendingIntent);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                Log.e("SHYAM", "e   " + e.getMessage());
            }
        });
    }
}
