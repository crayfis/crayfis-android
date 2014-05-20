package edu.uci.crayfis;

/*********************************************
 * 
 * Simple class to store data in a file and upload data to a server
 */


import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.hardware.Camera;
import android.provider.Settings.Secure;
import android.util.Log;

import java.util.ArrayList;

public class DataStorage {
	public static final String TAG = "DataStorage";
	
	// details of the run
	public String run_name;
	public String uname;
	public String umail;
	public boolean anon;
	public long start_time;
	public double longitude;
	public double latitude;
	public int threshold;
	public int sdkv;
	public String phone_model;
	
	public String server_address;
	public String server_port;
	public String upload_uri;
	
	public String versionName = null;
	public int versionCode = -1;
	
	// next index into 'files' array, where to store filename
	public int writeIndex;
	// next index into 'files' array, file to upload and delete
	public int readIndex;
	
	// maximum time to keep files (milliseconds)
	public long maxFileAge = 120*1000;
	
	// avoid large files
	private int max_particles_per_file=1000;
	
	// avoid overflowing memory
	private int max_files=500;

	// names of files written to
	String[] files = new String[max_files];

	// files are either 
	//   EMPTY = name invalid, no particles stored
	//   VALID = name valid, file exists, not full
	//   FULL  = name valid, file exists, full
	status[] current_status = new status[max_files];
	long[] creation_time = new long[max_files];
	
	private enum status  { FULL,VALID, EMPTY };

	// number of particles currently stored in files 
	public int[] stored = new int[max_files];
	
	// setter
	public void setRunName(String r) { run_name=r;}
		
	// basics of filenames
	public String basename="particle_data_source";
	public String basename_calib="calibration_source";

	public String extension="txt";
	
	public String id;
	
	// the file output stream
	FileOutputStream fos;
	
	// application context, needed for file storage
	Context context;
	
	// create the name of the file for storage
	String update_storage_name(String base)
	{
		// new file name
		String storage = base+"_run"+run_name+"_id"+id+"_time"+System.currentTimeMillis()+"."+extension;
		return storage;
	}
	
	// gets the context from the main activity class
	public DataStorage(Context ocontext)
	{
	  context = ocontext;
	  id = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
	  
	  // initialize the counters
	  for (int i=0;i<max_files;i++) 
	  {	
		  current_status[i]=DataStorage.status.EMPTY;
		  stored[i] =0;
	  }
	  readIndex=0;
	  writeIndex=0;
	 }
	
	// generate the file header from current info
	public String generate_header()
	{
		String header = "@@@ name "+uname+" email "+umail+" anon "+anon+" model "+phone_model+" sdkvers"+sdkv
					+" version_name"+versionName+" version_code"+versionCode
					+" currtime "+java.lang.System.currentTimeMillis()
					+"  starttime "+start_time + " loc "+longitude+" "+latitude+" threshold "+threshold+"\n";
		return header;
	}
	
	public void upload_calibration_hist(int[] histogram)
	{
		String cfile = update_storage_name(basename_calib);
		// open the file, append mode
		try{
		fos = context.getApplicationContext().openFileOutput(cfile,android.content.Context.MODE_APPEND);
		//write the header if it hasn't been written yet
		
		fos.write( generate_header().getBytes() );
	
		// write the current particles, starting from the end in case we run out of space
		for (int i=0;i<256;i++)
		{	
			// zero suppress
			if (histogram[i]>0)
			{
			String line = i+" "+histogram[i]+"\n";
		
			fos.write(line.getBytes());
			}
		}
			
		fos.close();
		uploadFile(cfile);
		deleteFile(cfile);
		} catch (Exception e) { Log.d("calibration","Exception = "+e.getMessage());}
		
	}
	
	// store these particles in the current open file, if there is one not full
	public int write_particles(ParticleData particles[],int size)
	{
		if (size>0)
		{
			Log.d("datastorage","Got "+size+" particles to write. Index is "+writeIndex+" status is "+current_status[writeIndex]);
				
			boolean valid_file = openCurrentFile();
			
			// only write if there is an empty file or non-full file			
			if (valid_file) {
				// write the current particles, starting from the end in case we run out of space
				for (int i=size-1;i>=0;i--)
				{	
					String line = i+" "+particles[i].x
							+"  "+particles[i].y
							+"  "+particles[i].val
							+"  "+particles[i].nearAve
							+"  "+particles[i].nearAve25
							+"  "+particles[i].nearMax
							+"  "+particles[i].time
							+"  "+particles[i].lon
							+"  "+particles[i].lat
							+"\n";
					try {
						fos.write(line.getBytes());
					}
					catch (IOException ex) {
						Log.e("datastorage", "Error writing to file!", ex);
					}
					
					stored[writeIndex]++;
				
					// avoid writing the whole thing to one file, to limit file size
					if (stored[writeIndex]>=max_particles_per_file)
					{
						Log.d("datastorage","File "+writeIndex+" is full Nstored="+stored[writeIndex]);
						
						// okay, we've filled up the file. close out the current file and get a new one.
						closeCurrentFile();
						valid_file = openCurrentFile();
						
						if (! valid_file) {
							// no more room, return how many particles remain on list
							Log.d("datastorage"," Next file full. Not writing "+(i+1)+" particles");
							return i+1;
						}
						
				    }
			   }
			Log.d("datastorage","Saved particles to file at index "+writeIndex+" Nstored="+stored[writeIndex]);
			}
	}
		
		return 0;
}
	
	/**
	 * Close out the current file, making it available to be uploaded.
	 */
	public void closeCurrentFile() {
		try {
			fos.close();
		}
		catch (IOException ex) {
			// I have no idea what this means...
			Log.e("datastorage", "Unable to close file pointer in closeCurrentFile()!", ex);
		}
		
		current_status[writeIndex] = DataStorage.status.FULL;
		
		// increment the write index.
		writeIndex = (writeIndex + 1) % max_files;
		return;
	}
	
	/**
	 * Close out the current file if it is older than maxFileAge.
	 */
	public void closeCurrentFileIfOld() {
		openCurrentFile();
		if ( (System.currentTimeMillis() - creation_time[writeIndex]) > maxFileAge) {
			Log.d("datastorage", "Closing out old file; it has " + stored[writeIndex] + " entries.");
			closeCurrentFile();
		}
	}
	
	/**
	 * Open the current file, creating it if necessary.
	 * Returns true on success, false otherwise.
	 */
	private boolean openCurrentFile() {
		try {
			if (fos != null)
				fos.close();
		}
		catch (IOException ex) {
			// Not sure what this means... probably there wasn't
			// already an open file handle, which is fine.
			Log.e("datastorage", "Unable to close file pointer in openCurrentFile()!", ex);
		}
		
		// if file is FULL, it means we have lapped the data upload
		// and should write no more data into files --> backpressure!
		if (current_status[writeIndex] == DataStorage.status.FULL) {
			return false;
		}
		
		// file is either VALID or EMPTY
		// if EMPTY, there is no current file, create a new one
		boolean new_file = false;
		if (current_status[writeIndex]==DataStorage.status.EMPTY)
		{
			new_file = true;
			
			Log.d("datastorage","creating file at index "+writeIndex);
			files[writeIndex]=update_storage_name(basename);
			stored[writeIndex]=0;
			creation_time[writeIndex] = System.currentTimeMillis();
			current_status[writeIndex]=DataStorage.status.VALID;
		}
		
		try {
			// open the file, append mode.
			fos = context.getApplicationContext().openFileOutput(files[writeIndex],android.content.Context.MODE_APPEND);
		}
		catch (FileNotFoundException ex) {
			Log.e("datastorage","Could not open file error: "+ex.getMessage(), ex);
			return false;
		}
		
		// write out the header, if this is a new file.
		if (new_file) {
			try {
				fos.write( generate_header().getBytes() );
			}
			catch (IOException ex) {
				Log.e("datastorage", "Error writing header to new file!"+ex.getMessage(), ex);
				return false;
			}
		}
		
		return true;
	}
	
	// zero out the file
	public void deleteFile(String filen)
	{
		try{
		File dir = context.getApplicationContext().getFilesDir();
		File file = new File(dir,filen);
		file.delete();
		Log.d("datastorage","Deleted data file "+filen);
		} catch (Exception e) 
		{  Log.e("reco","Could not clear storage file"+filen);}
	}	
	
	public int uploadFiles()
	{
		int numUploaded=0;
		while (current_status[readIndex]==DataStorage.status.FULL) 
		{
			Log.d("data","uploading file"+files[readIndex]);
			uploadFile(files[readIndex]);
			numUploaded++;
			deleteFile(files[readIndex]);
			stored[readIndex]=0;
			current_status[readIndex]=DataStorage.status.EMPTY;
			readIndex++;
			if (readIndex==max_files) readIndex=0;
		}
	
		return numUploaded;
	}
	
	
// upload any file	
public int uploadFile(String sourceFileUri) {
		String uploadServerUri = "http://"+server_address+":"+server_port+upload_uri;
        Log.d(TAG, "uploading file to: " + uploadServerUri);
        String fileName = sourceFileUri;
        int serverResponseCode=0;
        HttpURLConnection conn = null;
        DataOutputStream dos = null;  
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024; 
        File dir = context.getApplicationContext().getFilesDir();
        File sourceFile = new File(dir,sourceFileUri); 
         
        if (!sourceFile.isFile()) {
             
              
             Log.e("uploadFile", "Source File not exist :"+sourceFileUri);
                                                  
              
             return 0;
          
        }
        else
        {
             try { 
                  
                   // open a URL connection to the Servlet
                 FileInputStream fileInputStream = new FileInputStream(sourceFile);
                 URL url = new URL(uploadServerUri);
                  
                 // Open a HTTP  connection to  the URL
                 conn = (HttpURLConnection) url.openConnection(); 
                 conn.setDoInput(true); // Allow Inputs
                 conn.setDoOutput(true); // Allow Outputs
                 conn.setUseCaches(false); // Don't use a Cached Copy
                 conn.setRequestMethod("POST");
                 conn.setRequestProperty("Connection", "Keep-Alive");
                 conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                 conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                 conn.setRequestProperty("uploaded_file", fileName); 
                  
                 dos = new DataOutputStream(conn.getOutputStream());
        
                 dos.writeBytes(twoHyphens + boundary + lineEnd); 
                 dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                                           + fileName + "\"" + lineEnd);
                  
                 dos.writeBytes(lineEnd);
        
                 // create a buffer of  maximum size
                 bytesAvailable = fileInputStream.available(); 
        
                 bufferSize = Math.min(bytesAvailable, maxBufferSize);
                 buffer = new byte[bufferSize];
        
                 // read file and write it into form...
                 bytesRead = fileInputStream.read(buffer, 0, bufferSize);  
                    
                 while (bytesRead > 0) {
                      
                   dos.write(buffer, 0, bufferSize);
                   bytesAvailable = fileInputStream.available();
                   bufferSize = Math.min(bytesAvailable, maxBufferSize);
                   bytesRead = fileInputStream.read(buffer, 0, bufferSize);   
                    
                  }
        
                 // send multipart form data necesssary after file data...
                 dos.writeBytes(lineEnd);
                 dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
        
                 // Responses from the server (code and message)
                 serverResponseCode = conn.getResponseCode();
                 String serverResponseMessage = conn.getResponseMessage();
                   
                 Log.i("uploadFile", "HTTP Response is : "
                         + serverResponseMessage + ": " + serverResponseCode);
                  
                 if(serverResponseCode == 200){
                      Log.d("upload","upload complete");
                                
                 }    
                  
                 //close the streams //
                 fileInputStream.close();
                 dos.flush();
                 dos.close();
                   
            } catch (MalformedURLException ex) {
             
                Log.e("Upload file to server", "error: " + ex.getMessage(), ex);  
            } catch (Exception e) {
                 
               
                Log.e("Upload file to server Exception", "Exception : "
                                                 + e.getMessage(), e);  
            }
                
            return serverResponseCode; 
             
         } // End else block 
       } 

	
	
	
	
	
	
	
}
