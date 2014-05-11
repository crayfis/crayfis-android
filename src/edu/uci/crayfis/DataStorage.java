package edu.uci.crayfis;

/*********************************************
 * 
 * Simple class to store data in a file and upload data to a server
 */


import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.hardware.Camera;
import android.provider.Settings.Secure;
import android.util.Log;

import java.util.ArrayList;

public class DataStorage {
	
	
	// details of the run
	public String run_name;
	public String uname;
	public String umail;
	public boolean anon;
	public long start_time;
	public double longitude;
	public double latitude;
	public int threshold;
	
	// next index into 'files' array, where to store filename
	public int writeIndex;
	// next index into 'files' array, file to upload and delete
	public int readIndex;
	
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
		String header = "@@@ name "+uname+" email "+umail+" anon "+anon
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
			try{
				
			// only write if there is an empty file or non-full file
			// if file is FULL, it means we have lapped the data upload
			// and should write no more data into files --> backpressure!
			if (current_status[writeIndex]!=DataStorage.status.FULL)
			{
				// file is either VALID or EMPTY
				// if EMPTY, there is no current file, create a new one	
				if (current_status[writeIndex]==DataStorage.status.EMPTY)
				{
						Log.d("datastorage","creating file at index "+writeIndex);
						files[writeIndex]=update_storage_name(basename);
						stored[writeIndex]=0;
						current_status[writeIndex]=DataStorage.status.VALID;
				}
						
				// open the file, append mode
				fos = context.getApplicationContext().openFileOutput(files[writeIndex],android.content.Context.MODE_APPEND);
				//write the header if it hasn't been written yet
				if (stored[writeIndex]==0)
					fos.write( generate_header().getBytes() );
			
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
					fos.write(line.getBytes());
					stored[writeIndex]++;
				
					// avoid writing the whole thing to one file, to limit file size
					if (stored[writeIndex]>=max_particles_per_file)
					{
						Log.d("datastorage","File "+writeIndex+" is full Nstored="+stored[writeIndex]);
						fos.close();
						current_status[writeIndex] = DataStorage.status.FULL;
					
						// go to next index
						writeIndex++;
						if (writeIndex==max_files) writeIndex=0;
					
						if (current_status[writeIndex] == DataStorage.status.EMPTY)
						{						
							files[writeIndex]=update_storage_name(basename);
							stored[writeIndex]=0;
							fos = context.getApplicationContext().openFileOutput(files[writeIndex],android.content.Context.MODE_APPEND);
							if (stored[writeIndex]==0)
								fos.write( generate_header().getBytes() );
						}
						else
						{
							Log.d("datastorage"," Next file full. Not writing "+(i+1)+" particles");
							// no more room, return how many particles remain on list
							return i+1;
						}
						
				    }
			   }
			fos.close();
			current_status[writeIndex] = DataStorage.status.VALID;
			Log.d("datastorage","Saved particles to file at index "+writeIndex+" Nstored="+stored[writeIndex]);
			}
		} catch (Exception e) { Log.d("datastorage","Could not open file error: "+e.getMessage());}
	}
		
		return 0;
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
	
		// always upload at least one file, even if empty
		if (numUploaded==0)
		{
			
			try{
				
			// make a file with just header info
			String pingFile=update_storage_name(basename);
			fos = context.getApplicationContext().openFileOutput(pingFile,android.content.Context.MODE_APPEND);
			fos.write( generate_header().getBytes() );
			fos.close();
			
			uploadFile(pingFile);
			deleteFile(pingFile);
							
			} catch (Exception e) 
			{  Log.e("reco","Could not clear storage file");}

		}
	
		return 0;
	}
	
	
// upload any file	
public int uploadFile(String sourceFileUri) {
        
        String upLoadServerUri = "http://crayfis.ps.uci.edu/data/data.php";
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
                 URL url = new URL(upLoadServerUri);
                  
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
