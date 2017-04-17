package edu.uci.crayfis;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import edu.uci.crayfis.camera.RawCameraFrame;
import edu.uci.crayfis.util.CFLog;

/**
 * Created by Jeff on 4/16/2017.
 */

public class CFLocation implements com.google.android.gms.location.LocationListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // New API
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    // Old API
    private LocationManager mLocationManager;
    private android.location.LocationListener mLocationListener;
    private Location mLastLocationDeprecated;

    private final RawCameraFrame.Builder BUILDER;

    private static CFLocation sInstance;

    public static CFLocation getInstance(Context context, RawCameraFrame.Builder frameBuilder) {
        if(sInstance == null) {
            sInstance = new CFLocation(context, frameBuilder);
        }
        return sInstance;
    }

    private CFLocation(Context context, final RawCameraFrame.Builder frameBuilder) {
        BUILDER = frameBuilder;

        register(context);
    }

    private void register(Context context) {
        newLocation(new Location("BLANK"), false);

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();
        // backup location if Google play isn't working or installed
        mLocationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);

        try {
            mLastLocationDeprecated = getLocationDeprecated();
        } catch(SecurityException e) {
            // TODO: tell the user to turn on location settings
        }

        newLocation(mLastLocationDeprecated, true);
    }

    public void unregister() {
        mGoogleApiClient.disconnect();
        if(mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
            mLocationManager = null;
        }
        sInstance = null;
    }

    private boolean location_valid(Location location)
    {

        return (location != null
                && java.lang.Math.abs(location.getLongitude())>0.1
                && java.lang.Math.abs(location.getLatitude())>0.1);

    }

    private void newLocation(Location location, boolean deprecated)
    {

        if (!deprecated)
        {
            //  Google location API
            // as long as it's valid, update the data
            if (location != null)
                CFApplication.setLastKnownLocation(location);
                BUILDER.setLocation(location);
        } else {
            // deprecated interface as backup

            // is it valid?
            if (location_valid(location))
            {
                // do we not have a valid current location?
                if (!location_valid(CFApplication.getLastKnownLocation()))
                {
                    // use the deprecated info if it's the best we have
                    CFApplication.setLastKnownLocation(location);
                    BUILDER.setLocation(location);
                }
            }

        }
        //CFLog.d("## newLocation data "+location+" deprecated? "+deprecated+" -> current location is "+CFApplication.getLastKnownLocation());

    }

    private Location getLocationDeprecated() throws SecurityException
    {
        if (mLocationListener==null) {
            mLocationListener = new android.location.LocationListener() {
                // deprecated location update interface
                public void onLocationChanged(Location location) {
                    // Called when a new location is found by the network location
                    // provider.
                    //CFLog.d("onLocationChangedDeprecated: new  location = "+location);

                    mLastLocationDeprecated = location;

                    // update the location in case the Google method failed
                    newLocation(location,true);

                }

                public void onStatusChanged(String provider, int status,
                                            Bundle extras) {
                }

                public void onProviderEnabled(String provider) {
                }

                public void onProviderDisabled(String provider) {
                }
            };
        }

        // ask for updates from network and GPS
        try {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        } catch (RuntimeException e)
        { // some phones do not support
        }
        // get the last known coordinates for an initial value
        Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (null == location) {
            location = new Location("BLANK");
        }
        return location;
    }

    @Override
    public void onConnected(Bundle connectionHint) {

        // first get last known location
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        CFLog.d("onConnected: asking for location = "+mLastLocation);

        // set the location; if this is false newLocation() will disregard it
        newLocation(mLastLocation,false);

        // request updates as well
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);


    }

    // Google location update interface
    public void onLocationChanged(Location location)
    {
        //CFLog.d("onLocationChanged: new  location = "+mLastLocation);

        // set the location; if this is false newLocation() will disregard it
        newLocation(location,false);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
    }

    public String getStatus() {
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
                + (CFApplication.getLastKnownLocation() != null ? " Official location = (long=" + CFApplication.getLastKnownLocation().getLongitude()
                +" lat="+CFApplication.getLastKnownLocation().getLatitude() : "") + "\n";

    }

}
