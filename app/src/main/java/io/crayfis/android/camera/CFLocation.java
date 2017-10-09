package io.crayfis.android.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import io.crayfis.android.CFApplication;
import io.crayfis.android.R;
import io.crayfis.android.util.CFLog;

/**
 * Created by Jeff on 4/16/2017.
 */

class CFLocation implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    Location currentLocation;

    // New API
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private long mLastConnectionAttempt;
    private com.google.android.gms.location.LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location)
        {
            // set the location; if this is false newLocation() will disregard it
            mLastLocation = location;
            updateLocation(location, false);
            if(System.currentTimeMillis() - mLastConnectionAttempt > 300000) {
                mGoogleApiClient.connect();
            }
        }
    };

    // Old API
    private LocationManager mLocationManager;
    private Location mLastLocationDeprecated;
    private android.location.LocationListener mLocationListenerDeprecated = new android.location.LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location
            // provider.
            //CFLog.d("onLocationChangedDeprecated: new  location = "+location);

            mLastLocationDeprecated = location;

            // update the location in case the Google method failed
            updateLocation(location,true);

        }

        @Override
        public void onStatusChanged(String provider, int status,
                                    Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };


    private final RawCameraFrame.Builder RCF_BUILDER;
    private final Context CONTEXT;

    CFLocation(Context context, final RawCameraFrame.Builder frameBuilder) {
        CONTEXT = context;
        RCF_BUILDER = frameBuilder;

        updateLocation(new Location("BLANK"), false);

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();
        mLastConnectionAttempt = System.currentTimeMillis();
    }

    void unregister() {
        mGoogleApiClient.disconnect();
        if(mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListenerDeprecated);
            mLocationManager = null;
        }
    }


    @Override
    public void onConnected(Bundle connectionHint) {
        // get rid of old API if present
        if(mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListenerDeprecated);
            mLocationManager = null;
        }

        try {
            // get last known location
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            CFLog.d("onConnected: asking for location = " + mLastLocation);

            // set the location; if this is false updateLocation() will disregard it
            updateLocation(mLastLocation, false);

            // request updates as well
            LocationRequest locationRequest = new LocationRequest();
            locationRequest.setInterval(10000);
            locationRequest.setFastestInterval(5000);
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, mLocationListener);
        } catch (SecurityException e) {
            CFApplication application = (CFApplication) CONTEXT.getApplicationContext();
            application.userErrorMessage(R.string.quit_permission, true);
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result)
    {
        CFLog.e("Failed to connect to Google Location Services");
        useDeprecated();
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
        CFLog.e("Google Location Services suspended");
        useDeprecated();
    }

    private void useDeprecated() {
        if (mLocationManager == null) {
            // backup location if Google play isn't working or installed
            mLocationManager = (LocationManager) CONTEXT.getSystemService(Context.LOCATION_SERVICE);

            try {
                // ask for updates from network and GPS
                try {
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListenerDeprecated);
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListenerDeprecated);
                } catch (RuntimeException e)
                { // some phones do not support
                }
                // get the last known coordinates for an initial value
                Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (null == location) {
                    location = new Location("BLANK");
                }
                mLastLocationDeprecated = location;
            } catch(SecurityException e) {
                CFApplication application = (CFApplication) CONTEXT.getApplicationContext();
                application.userErrorMessage(R.string.quit_permission, true);
            }
        }

        updateLocation(mLastLocationDeprecated, true);
    }

    private boolean isLocationValid(Location location)
    {
        return (location != null
                && java.lang.Math.abs(location.getLongitude())>0.1
                && java.lang.Math.abs(location.getLatitude())>0.1);

    }

    private void updateLocation(Location location, boolean deprecated)
    {

        if (!deprecated)
        {
            //  Google location API
            // as long as it's valid, update the data
            if (location != null) {
                currentLocation = location;
            }
        } else {
            // deprecated interface as backup

            // is it valid?
            if (isLocationValid(location))
            {
                // do we not have a valid current location?
                if (!isLocationValid(mLastLocation))
                {
                    // use the deprecated info if it's the best we have
                    currentLocation = location;
                }
            }

        }

        RCF_BUILDER.setLocation(currentLocation);
    }

    boolean isReceivingUpdates() {

        // first see if location services are on
        try {
            int locationMode = Settings.Secure.getInt(CONTEXT.getContentResolver(), Settings.Secure.LOCATION_MODE);
            if(locationMode == Settings.Secure.LOCATION_MODE_OFF) return false;

        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        // then make sure we have the right permissions
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                CONTEXT.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

    }

    String getStatus() {
        return (mLastLocation != null ? "Current google location: (long=" + mLastLocation.getLongitude()
                + ", lat=" + mLastLocation.getLatitude()
                + ") accuracy = " + mLastLocation.getAccuracy()
                + " provider = " + mLastLocation.getProvider()
                + " time=" + mLastLocation.getTime() : "") + "\n"
                + (mLastLocationDeprecated != null ? "Current android location: (long=" + mLastLocationDeprecated.getLongitude()
                + ", lat=" + mLastLocationDeprecated.getLatitude()
                + ") accuracy = " + mLastLocationDeprecated.getAccuracy()
                + " provider = " + mLastLocationDeprecated.getProvider()
                + " time=" + mLastLocationDeprecated.getTime() : "") + "\n"
                + (currentLocation != null ? " Official location = (long=" + currentLocation.getLongitude()
                +" lat="+currentLocation.getLatitude() : "") + "\n";

    }

}
