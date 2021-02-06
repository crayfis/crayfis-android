package io.crayfis.android.daq;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import io.crayfis.android.exposure.Frame;
import io.crayfis.android.main.CFApplication;
import io.crayfis.android.R;
import io.crayfis.android.util.CFLog;


/**
 * Created by Jeff on 4/16/2017.
 */

class CFLocation extends LocationCallback implements OnFailureListener {

    private Location mCurrentLocation;
    private Location mLastLocation;

    // New API
    private FusedLocationProviderClient mFusedLocationClient;
    private final LocationRequest mLocationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(60000L); // 10 min

    private final Frame.Builder FRAME_BUILDER;
    private Context mContext;

    CFLocation(Frame.Builder builder) {
        FRAME_BUILDER = builder;
    }

    void register(Context context) {
        mContext = context;

        updateLocation(new Location("BLANK"));

        // build the clients
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);

        requestLocationUpdates();
    }


    void unregister() {
        mFusedLocationClient.removeLocationUpdates(this);
        mFusedLocationClient.flushLocations();
    }

    private boolean checkPermission() {
        int permissionFine = ActivityCompat.checkSelfPermission(mContext,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionFine == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationUpdates() {
        if (!checkPermission()) {
            CFApplication application = (CFApplication) mContext.getApplicationContext();
            application.userErrorMessage(true, R.string.quit_permission);
            return;
        }

        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        updateLocation(location);
                    }
                })
                .addOnFailureListener(this);


        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                this,
                Looper.getMainLooper())
                .addOnFailureListener(this);
    }

    @Override
    public void onLocationResult(LocationResult locationResult) {
        if (locationResult == null) {
            updateLocation(null);
        } else {
            updateLocation(locationResult.getLastLocation());
        }
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        ApiException apiException = (ApiException) e;
        if(apiException.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
            ResolvableApiException resolvable = (ResolvableApiException) apiException;
            CFLog.e("Resolvable!");
        }
    }

    private void updateLocation(Location location) {
        // update the data
        mLastLocation = location;
        if (location != null) {
            mCurrentLocation = location;
        }

        FRAME_BUILDER.setLocation(mCurrentLocation);
    }

    boolean isReceivingUpdates() {

        // obviously not if this hasn't been registered
        if(mContext == null) return false;

        // first see if location services are on
        try {
            int locationMode = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.LOCATION_MODE);
            if(locationMode == Settings.Secure.LOCATION_MODE_OFF) return false;

        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        // then make sure we have the right permissions
        return checkPermission();

    }

    Location getLastKnownLocation() {
        return mCurrentLocation;
    }

    String getStatus() {
        return (mLastLocation != null ? "Most recent location:\n"
                + "(" + mLastLocation.getLongitude()
                + ", " + mLastLocation.getLatitude() + ")\n"
                + "provider = " + mLastLocation.getProvider() + "\n"
                + "accuracy = " + mLastLocation.getAccuracy()
                + ", time=" + mLastLocation.getTime() + "\n" : "")
                + (mCurrentLocation != null ? "Official location:\n"
                + "(" + mCurrentLocation.getLongitude()
                + ", " + mCurrentLocation.getLatitude() + ")" : "null") + "\n";

    }

}
