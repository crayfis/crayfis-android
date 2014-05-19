
 /* Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package edu.uci.crayfis;
import java.lang.Math;

import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;

import java.io.DataOutputStream;
//import android.preference.PreferenceFragment;
//import android.preference.PreferenceManager;
//import android.content.SharedPreferences;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

import edu.uci.crayfis.R;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

/**
 * This is the main Activity of the app; this activity is started when
 * the user hits "Run" from the start screen. Here we manage the threads
 * that acquire, process, and upload the pixel data.
 */
public class DAQActivity extends Activity implements Camera.PreviewCallback {

	public static final String TAG = "DAQActivity";
	
	// camera and display objects
	private Camera mCamera;
	private Visualization mDraw;
	private CameraPreview mPreview;
	
	// WakeLock to prevent the phone from sleeping during DAQ
	PowerManager.WakeLock wl;
	

	private char[][] histo_chars = new char[256][256];
	private int histo_max=0;
	// draw some X axis labels
	char[] labels = new char[256];
// settings
	private boolean uploadData=true;
	
	
	private int maxFrames=2;
	// current data, where frame integrating is happening
	byte[][] current = new byte[maxFrames][80000];
	status[] current_status = new status[maxFrames];
	
	// to keep track of height/width
	private Camera.Size s;
	
	// Android image data used for displaying the results
	//private Bitmap output;
	
	private enum status  { VALID, EMPTY };
	
	// how many events L1 skipped (after prescale)
	private int L1skip=0;
	// how many events L1 processed (after prescale)
	private int L1proc=0;
	private int L1pass=0;
	
	// how many events L1 skipped (after prescale)
	private int L2skip=0;
	// how many events L1 processed (after prescale)
	private int L2proc=0;
	
	// how many particles seen > thresh
	private int numHits=0;
	private int numFiles=0;
	private int numUploads=0;
	
	private long L1counter=0;
	private long L2counter=0;

	private int L1prescale=1;
	private int L2prescale=1;
	
	private int uploadDelta = 60; // max seconds to wait before uploading a file
	
	
	private enum state { CALIBRATION, DATA};
	private state current_state;
	//private boolean doingCalibration;
	private boolean fixed_threshold;
	private long  calibration_start;
	
	
	// L1 hit threshold 
	private int L1thresh=0;
	long starttime;
	long lastUploadTime;
	long lastL2time;
	private int writeIndex;
	private int readIndex;

	// Location stuff
	private Location currentLocation;
	private LocationManager locationManager;
	private LocationListener locationListener;
	
	// Thread where image data is processed for L2
	private ThreadProcess thread;

	// thread to upload the data
	private UploadProcess uploadthread;
	
	// class to find particles in frames
	private ParticleReco reco;
	
	// class to store data to file and upload it
	private DataStorage dstorage;

	// Object used for synchronizing output image
	private final Object lockOutput = new Object();
	
	Context context;

	
	public void clickedRegister(View view) {

	        Intent i = new Intent(this, UserRegisterActivity.class);
	        startActivity(i); 
	}
	
	public void clickedAbout(View view) {
		
		final SpannableString s = new SpannableString("crayfis.ps.uci.edu/about");

	    final TextView tx1 = new TextView(this);
	    tx1.setText("CRAYFIS is an app which uses your phone to look for cosmic ray particles. More details:  "
	            + s);
	            
	    tx1.setAutoLinkMask(RESULT_OK);
	    tx1.setMovementMethod(LinkMovementMethod.getInstance());

	    Linkify.addLinks(s, Linkify.WEB_URLS);
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle("About CRAYFIS")
	            .setCancelable(false)
	            .setPositiveButton("OK",
	                    new DialogInterface.OnClickListener() {
	                        public void onClick(DialogInterface dialog,
	                                int id) {
	                        }
	                    })

	            .setView(tx1).show();
		/*
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
				
		
 
		// set title
		alertDialogBuilder.setTitle("About CRAYFIS");
 
		// set dialog message
		alertDialogBuilder
			.setMessage(Html.fromHtml("CRAYFIS: <a href=\"http://crayfis.ps.uci.edu\">details</a>"))
				.setCancelable(false)
				.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						
					}
				  });
 
				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();
 
				// show it
				alertDialog.show();
*/
	}	
	
	private void newLocation(Location location)
	{
		currentLocation = location;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/* for debugging
		 PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		 wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
		 wl.acquire();
		 */
		
	//	getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);

		// Used to visualize the results
		mDraw = new Visualization(this);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this,this,true);
		
		reco = new ParticleReco();
		context = getApplicationContext();

		dstorage = new DataStorage(context);
		// Resolve the build version for upload purposes.
		try {
			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			dstorage.versionName = pInfo.versionName;
			dstorage.versionCode = pInfo.versionCode;
			
			Log.d(TAG, "resolved versionName = " + dstorage.versionName + ", versionCode = " + dstorage.versionCode);
		}
		catch (PackageManager.NameNotFoundException ex) {
			Log.e(TAG, "Couldn't resolve package version", ex);
		}
		
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);
		preview.addView(mDraw);
		writeIndex=0;
		readIndex=0;
		L1counter=L1proc=L1skip=L1pass=0;
		starttime=	System.currentTimeMillis();
		
		LocationListener locationListener = new LocationListener() {
		    public void onLocationChanged(Location location) {
		      // Called when a new location is found by the network location provider.
		      newLocation(location);
		    }

		    public void onStatusChanged(String provider, int status, Bundle extras) {}

		    public void onProviderEnabled(String provider) {}

		    public void onProviderDisabled(String provider) {}
		  };
		// gget the manager
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		// ask for updates from network and GPS
		//locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
		//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

		// get the last known coordinates for an initial value
		currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (null == currentLocation) { currentLocation = new Location("BLANK");}
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		L1thresh = Integer.parseInt(sharedPrefs.getString("prefThreshold","0"));
		if (L1thresh>=0) 
		{
			fixed_threshold=true;
			current_state = DAQActivity.state.DATA;

		}
		else
		{
			fixed_threshold=false;
			for (int i=0;i<256;i++)
				reco.histogram[i]=0;
			calibration_start= System.currentTimeMillis();
			current_state = DAQActivity.state.CALIBRATION;
		}
		
		String name = sharedPrefs.getString("prefRunName","NULL");
		dstorage.setRunName(name);
		dstorage.anon = sharedPrefs.getBoolean("prefAnon", false);
		dstorage.uname = sharedPrefs.getString("prefUserName", "DefaultName");
		dstorage.umail = sharedPrefs.getString("prefUserEmail", "DefaultMail");
		dstorage.sdkv = android.os.Build.VERSION.SDK_INT;
		dstorage.phone_model = android.os.Build.MANUFACTURER+android.os.Build.MODEL;
		dstorage.start_time = starttime;
		dstorage.latitude = currentLocation.getLatitude();
		dstorage.longitude = currentLocation.getLongitude();
		dstorage.server_address = getString(R.string.server_address);
		dstorage.server_port = getString(R.string.server_port);
		dstorage.upload_uri = getString(R.string.upload_uri);
		for (int i=0;i<maxFrames;i++) current_status[i]=DAQActivity.status.EMPTY;
	}
	
	@Override
	protected void onDestroy() { /*wl.release();*/ super.onDestroy(); }	

	@Override
	protected void onResume() {
		super.onResume();

		if( mCamera != null )
			throw new RuntimeException("Bug, camera should not be initialized already");
		
		setUpAndConfigureCamera();

	}

	@Override
	protected void onPause() {
		super.onPause();

		// stop the camera preview and all processing
		if (mCamera != null){
			mPreview.setCamera(null);
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;

			thread.stopThread();
			thread = null;
			uploadthread.stopThread();
			uploadthread=null;
		}
	}

	/**
	 * Sets up the camera if it is not already setup.
	 */
	private void setUpAndConfigureCamera() {
		// Open and configure the camera
		mCamera = Camera.open();

		Camera.Parameters param = mCamera.getParameters();

		Log.d("params","Camera params are"+param.flatten());
		
		// Select the preview size closest to 320x240
		// Smaller images are recommended because some computer vision operations are very expensive
		List<Camera.Size> sizes = param.getSupportedPreviewSizes();
//		s = sizes.get(closest(sizes,640,480));
	    s = sizes.get(closest(sizes,320,240));
//		Camera.Size s = sizes.get(closest(sizes,176,144));

		Log.d("setup","size is width="+s.width+" height ="+s.height);
		param.setPreviewSize(s.width,s.height);
		//param.setFocusMode("FIXED");
		param.setExposureCompensation(0);
		mCamera.setParameters(param);

		// declare image data
		//output = Bitmap.createBitmap(s.width,s.height,Bitmap.Config.ARGB_8888 );

		// start image processing thread
		thread = new ThreadProcess();
		thread.start();

		// start data upload thread
		uploadthread = new UploadProcess();
		uploadthread.start();
		
		// Create an instance of Camera
		mPreview.setCamera(mCamera);
		
	}

	/**
	 * Goes through the size list and selects the one which is the closest specified size
	 */
	public static int closest( List<Camera.Size> sizes , int width , int height ) {
		int best = -1;
		int bestScore = Integer.MAX_VALUE;

		for( int i = 0; i < sizes.size(); i++ ) {
			Camera.Size s = sizes.get(i);

			Log.d("setup","size is w="+s.width+" x "+s.height);
			int dx = s.width-width;
			int dy = s.height-height;

			int score = dx*dx + dy*dy;
			if( score < bestScore ) {
				best = i;
				bestScore = score;
			}
		}

		return best;
	}

	
	/**
	 * Called each time a new image arrives in the data stream.
	 */
	@Override
	public void onPreviewFrame(byte[] bytes, Camera camera) {
		
		// prescale
		//Log.d("preview","Camera vals are"+camera.getParameters.flatten());
		//Log.d("preview"," ="+L1counter+" mod = "+L1counter%L1prescale);
		if (L1counter%L1prescale==0)
		{
			if (current_status[writeIndex] == DAQActivity.status.EMPTY)
			{
				// whether we keep this frame
				boolean pass=false;
				int length   = s.width*s.height;
				
				// loop through frame, copy data and look for hit
				for (int i=0;i< length;i++)
				{
					current[writeIndex][i] = bytes[i];
					
					// check for hit
					//if (current[writeIndex][i] > L1thresh) pass=true;
					pass=true;
				}
				
				L1proc++;
				//Log.d("preview","  to index "+writeIndex+" pass? "+pass);

				// if we found a hit, the data is there, mark it ready for L2 
				if (pass)
				{
					L1pass++;
					current_status[writeIndex] = DAQActivity.status.VALID;
					writeIndex++;
					if (writeIndex==maxFrames) writeIndex=0;
				}
			}
			else
			  L1skip++;
			
		}
		L1counter++;

		// Can only do trivial amounts of image processing inside this function or else bad stuff happens.
		// To work around this issue most of the processing has been pushed onto a thread and the call below
		// tells the thread to wake up and process another image
		
	}


	
	/////////////////////////////////////////
	
	
	
	/**
	 * Draws on top of the video stream for visualizing computer vision results
	 */
	private class Visualization extends SurfaceView {

		Activity activity;
		Paint mypaint; 
		Paint mypaint2; 
		Paint mypaint2_thresh;
		Paint mypaint3; 

		
		private String[] histo_strings_all = new String[256];
		private String[] histo_strings_thresh = new String[256];

		void makeHistogram(int data[], int min, String[] histo_strings)
		{
			int max=1;
			for (int i=0;i<256;i++)
				if (data[i]>max) max=data[i];
			histo_max = max;
			// make 256 vertical devisions
			// each one is at log(data)/log(max) * i /256

			int ip=0;
			// height loop
			
			for (int j=256;j>0;j--)
			{
				// width loop
				for (int i=0;i<256;i++)
				{
					if ( i < min || data[i]==0 || java.lang.Math.log(data[i]) < java.lang.Math.log(max)*(j/256.0))
					//if ( data[i]  < max*j/256.0 )
						histo_chars[j-1][i] = ' ';
					else
						histo_chars[j-1][i] = '*';
				}
				histo_strings[j-1] = new String(histo_chars[j-1]);
			}		
		}
		
		
		public Visualization(Activity context ) {
			super(context);
			this.activity = context;

			mypaint = new Paint();
			mypaint2 = new Paint();
			mypaint2_thresh = new Paint();

			mypaint3 = new Paint();

			// This call is necessary, or else the
			// draw method will not be called.
			setWillNotDraw(false);
		}

		@Override
		protected void onDraw(Canvas canvas){

			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
			
			//Log.d("frames","onDraw() called");
			boolean doReco = sharedPrefs.getBoolean("prefDoReco",true);
			
			synchronized ( lockOutput ) {
				int w = canvas.getWidth();
				int h = canvas.getHeight();

					
								// fill the window and center it
				double scaleX = w/(double)640.0;
				double scaleY = h/(double)480.0;

				double scale = Math.min(scaleX,scaleY);
				double tranX = (w-scale*640.0)/2;
				double tranY = (h-scale*480.0)/2;

				canvas.translate((float)tranX,(float)tranY);
				canvas.scale((float)scale,(float)scale);
				
				// draw some data text for debugging
				int tsize=25;
				int yoffset=-300;
				mypaint.setStyle(android.graphics.Paint.Style.FILL); 
				mypaint.setColor(android.graphics.Color.RED); 
				mypaint.setTextSize((int)(tsize*1.5)); 
				
				mypaint3.setStyle(android.graphics.Paint.Style.FILL); 
				mypaint3.setColor(android.graphics.Color.GRAY); 
				mypaint3.setTextSize(tsize); 
				
				mypaint2.setStyle(android.graphics.Paint.Style.FILL); 
				mypaint2.setColor(android.graphics.Color.WHITE); 
				mypaint2.setTextSize(tsize/(float)10.0);
				Typeface tf = Typeface.create("Courier",Typeface.NORMAL);
				mypaint2.setTypeface(tf);
				
				mypaint2_thresh.setStyle(android.graphics.Paint.Style.FILL); 
				mypaint2_thresh.setColor(android.graphics.Color.GREEN); 
				mypaint2_thresh.setTextSize(tsize/(float)10.0);
				
				float deadtime = L1skip/((float)L1skip+L1proc);
				float l2deadtime = L2skip/((float)L2skip+L2proc);
				
				float l1rate = (L1counter)/((float)1e-3*(float)(System.currentTimeMillis() -starttime));
				float l2rate = (L2counter)/((float)1e-3*(float)(System.currentTimeMillis() -starttime));
				//canvas.drawText("STATISTICS",250, yoffset+1*tsize, mypaint);
				canvas.drawText("Time: "+(int)(1.0e-3*(float)(System.currentTimeMillis() -starttime))+"s",200, yoffset+4*tsize, mypaint);
				//canvas.drawText("Frames "+L1proc+" pass?"+L1pass+" analyzed="+L2proc+" skipped="+L2skip,250,yoffset+3*tsize,mypaint);
				canvas.drawText("Hits : "+numHits, 200,yoffset+6*tsize,mypaint);
				//canvas.drawText("Loc: "+String.format("%1.2f",currentLocation.getLongitude())+", "+String.format("%1.2f",currentLocation.getLatitude()), 250,15+5*tsize,mypaint);
				//canvas.drawText("Data quality good? "+reco.good_quality,250,15+6*tsize,mypaint);
				
				canvas.drawText("Files: "+numUploads,200,yoffset+8*tsize,mypaint);
				//canvas.drawText("readIndex "+dstorage.readIndex+" stored= "+dstorage.stored[dstorage.readIndex],250,15+9*tsize,mypaint);

				///// Histogram
				makeHistogram(reco.histogram,0,histo_strings_all);
				for (int j=256;j>0;j--)
					canvas.drawText(histo_strings_all[j-1],50,(float)(yoffset+(256-j)*tsize/10.0),mypaint2);

				makeHistogram(reco.histogram,L1thresh,histo_strings_thresh);
				for (int j=256;j>0;j--)
					canvas.drawText(histo_strings_thresh[j-1],50,(float)(yoffset+(256-j)*tsize/10.0),mypaint2_thresh);

				for (int i=0;i<256;i++)
					if (i%10==0)
						labels[i]='|';
					else	
						labels[i]=' ';

				String slabels = new String(labels);
				String slabelsnum = new String("0              100               200");
				canvas.drawText(slabels,(float)(50),(float)(yoffset+(256+5)*tsize/10.0),mypaint2);
				canvas.drawText(slabelsnum,(float)(42),(float)(yoffset+(256+14)*tsize/10.0),mypaint3);

				canvas.drawText("Pixel value",(float)(50+20*tsize/10.0),(float)(yoffset+(256+25)*tsize/10.0),mypaint3);
				
				if (current_state==DAQActivity.state.CALIBRATION)
					canvas.drawText(current_state.toString()+" ("+(int)(1.0e-3*(float)(System.currentTimeMillis() -calibration_start))+"s)",200,yoffset+10*tsize,mypaint);
				else
					canvas.drawText(current_state.toString()+" ("+L1thresh+")",200,yoffset+10*tsize,mypaint);

				canvas.drawLine(195, yoffset+10*tsize, 195,yoffset+3*tsize, mypaint);
				
				//canvas.drawText("Threshold: "+L1thresh,250,15+12*tsize,mypaint);

				// Y axis labels
				
				
				// draw  grid for debugging
/*
				for (int x = -500;x<1500;x+=250)
					for (int y = -500;y<1500;y+=250)
						canvas.drawText("(x="+x+",y="+y+")",(float)x,(float)y,mypaint3);
				*/
				canvas.save();
				canvas.rotate(-90, (float)(50+-7*tsize/10.0),
						(float)(yoffset+(256-50)*tsize/10.0));
				canvas.drawText(String.format("Number of pixels"),
							    (float)(50+-7*tsize/10.0),
								(float)(yoffset+(256-50)*tsize/10.0)
								,mypaint3);
				canvas.restore();
					
			}
			
		}
	}

	private class UploadProcess extends Thread {
		// true if a request has been made to stop the thread
				volatile boolean stopRequested = false;
				// true if the thread is running and can process more data
				volatile boolean running = true;
				
				/**
				 * Blocks until the thread has stopped
				 */
				public void stopThread() {
					stopRequested = true;
					while( running ) {
						uploadthread.interrupt();
						Thread.yield();
					}
				}

				@Override
				public void run() 
					{
					SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
					boolean doUpload = sharedPrefs.getBoolean("prefUploadData",true);

						while( !stopRequested ) 
							{
							// check datastorage for size, upload if too bit
							// needs to be buffered if no connection
							//Log.d("uploads"," reco? "+doReco+" uploads? "+uploadData);
											
							// occasionally upload files
							if (uploadData)
								{
									// upload every 'uploadDelta' seconds
									if  ((java.lang.System.currentTimeMillis()/1000 - lastUploadTime > uploadDelta ))
									{	
										Log.d("upload_thread"," looking for files to upload");
										// we always upload at least one file 
										numUploads+=dstorage.uploadFiles();
										lastUploadTime = java.lang.System.currentTimeMillis()/1000;
									}
								}
							}
						
						running=false;
					}
	}
	
	/**
	 * External thread used to do more time consuming image processing
	 */
	private class ThreadProcess extends Thread {

		// true if a request has been made to stop the thread
		volatile boolean stopRequested = false;
		// true if the thread is running and can process more data
		volatile boolean running = true;
		
		/**
		 * Blocks until the thread has stopped
		 */
		public void stopThread() {
			stopRequested = true;
			while( running ) {
				thread.interrupt();
				Thread.yield();
			}
		}

		@Override
		public void run() {

			while( !stopRequested ) {

				
				SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
				boolean doReco = sharedPrefs.getBoolean("prefDoReco",true);
				
				//Log.d("thread"," checking for data at index "+readIndex+" status = "+current_status[readIndex]);
				if (current_status[readIndex] == DAQActivity.status.VALID)
				{
					// prescale
					if (L2counter%L2prescale==0)
					{	
						if (doReco)
						{
							// find particles
							//Log.d("thread"," processing data at "+readIndex);
							int old = reco.particles_size;
							dstorage.latitude = currentLocation.getLatitude();
							dstorage.longitude = currentLocation.getLongitude();
							boolean current_quality=reco.good_quality;
							
							// look for particles, store them internally to reco object
							reco.process(current[readIndex],s.width,s.height,L1thresh,currentLocation);

							// start calibration mode again if find bad data
							// continue to restart until good data comes in
							if (reco.good_quality==false && fixed_threshold==false)
							 {
									// clear histogram before calibration
									for (int i=0;i<256;i++)
										reco.histogram[i]=0;
									calibration_start= System.currentTimeMillis();
									current_state=DAQActivity.state.CALIBRATION;	
								
							} 

							// save the data to a file for uploading, if requested
							// this returns the number of particles left in the array
							//  in case the files are all full
							if (current_state==DAQActivity.state.DATA && reco.good_quality==true)
							{
								// if we have good data after a period of bad, we start the good
								// data counter again
								if (current_quality==false)
									dstorage.start_time= System.currentTimeMillis();

								// how many did we find? add to running total
								numHits += (reco.particles_size - old);
								
								Log.d("process_thread","Writing "+reco.particles_size+" particles to file");
								reco.particles_size = dstorage.write_particles(reco.particles,reco.particles_size);
							} else {
								Log.d("process_thread","Not writing "+reco.particles_size+" particles to file");

							}	
						}
						L2proc++;
					}
					else L2skip++;
					// done with this data
					current_status[readIndex] = DAQActivity.status.EMPTY;
					readIndex++;
					if (readIndex==maxFrames) readIndex=0;
					
					L2counter++;
				
				}
				
				// calibration analysis
				// calibration rate = number of seconds per background photon
				int stat_factor = 5; // run for stat_factor*calibration_rate until we get stat_factor photons
					// a larger stat factor will mean a more accurate threshold but longer calibration time
				int calibration_rate=30;
				// is it time to update the threshold?
				if (current_state==DAQActivity.state.CALIBRATION 
						&& (System.currentTimeMillis() - calibration_start)*1e-3 > stat_factor*calibration_rate)
				{
				
					
					int new_thresh=255;
					// number of photons above this threshold
					int above_thresh=0;
					do{
						above_thresh += reco.histogram[new_thresh];
						Log.d("calibration","threshold "+new_thresh+" obs= "+above_thresh+"/"+stat_factor);
						new_thresh--;
					} while(new_thresh>0 && above_thresh < stat_factor);	
					
					L1thresh=new_thresh;
					dstorage.threshold = L1thresh;
					// clear list of particles
					reco.particles_size=0;
					current_state=DAQActivity.state.DATA;
					dstorage.start_time= System.currentTimeMillis();

					// upload the calibration histogram
					dstorage.upload_calibration_hist(reco.histogram);
					
				}
				
				mDraw.postInvalidate();
			}			
			running = false;
		}
	}
}
