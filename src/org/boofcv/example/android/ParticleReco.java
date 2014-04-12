package org.boofcv.example.android;

/******************************************
 * 
 *  class to extract particle candidates from integrated camera frames
 * 
 */

import android.location.Location;
import android.util.Log;

import java.io.FileOutputStream;
import java.util.ArrayList;
import org.boofcv.example.android.ParticleData;
import android.graphics.Bitmap;
import android.content.Context;

public class ParticleReco {
	ParticleData[] particles;
	
	public int[] histogram = new int[256];
	public int histo_count = 0;
	int max_particles=80000;
	public ParticleReco()
	{
	 particles = new ParticleData[max_particles];
	 good_quality=false;
	 for (int i=0;i<256;i++) histogram[i]=0;
	 for (int i=0;i<max_particles;i++)
		 particles[i] = new ParticleData();
	}
	int particles_size=0;
	
	public boolean good_quality;
	public float background;
	public float variance;
	public int ncount;
	
	// take an image and look for hits
	public void process(byte[] vals, int width, int height, int thresh, Location location)
	{
		// first we measure the background and variance, but to save time only do it for every
			// stepW or stepH-th pixel
		int stepW = 10;
		int stepH = 10;
		
		// get mean background level 
		background=0;
		ncount=0;
		//Log.d("reco"," vals length is "+vals.length+" s/w="+width+"/"+height+" tot="+(width*height));
		for (int ix=0;ix < width;ix+= stepW)
				for (int iy=0;iy<height;iy+=stepH)
				{
					int val = vals[ix+width*iy]&0xFF; 
					background += (float)val;
					ncount++;
					//Log.d("particlereco",
						//	"background("+ix+","+iy+") = "+val+" av("+ncount+") = "+background);
				}
		if (ncount>0)
		  background = (float)background/((float)1.0*ncount);
		
		// calculate variance
		ncount=0;
		variance=0;
		for (int ix=0;ix < width;ix+= stepW)
			for (int iy=0;iy<height;iy+=stepH)
			{
				int val = vals[ix+width*iy]&0xFF; 
				variance += (val-background)*(val - background);
				ncount++;
				//Log.d("particlereco",
					//	"background("+ix+","+iy+") = "+val+" var("+ncount+") = "
						//		+(float)Math.sqrt((float)variance/((float)1.0*ncount)));
			}
		if (ncount>0)
		  variance = (float)Math.sqrt((float)variance/((float)1.0*ncount));
		
		// is the data good?
		good_quality = (background < 30 && variance < 5);
		Log.d("reco","background = "+background+" var = "+variance+" qual = "+good_quality);
		
		
		// if bad data, don't bother looking for particles
		if (!good_quality) return;
		
		// now look for outliers
		
		long time = System.currentTimeMillis();
		for (int ix=0;ix < width;ix++)
			for (int iy=0;iy<height;iy++)
			{
				int val = vals[ix+width*iy]&0xFF; 		
				histogram[val]++;
				histo_count++;
				if (val > thresh && particles_size<max_particles)
				{	
					//Log.d("reco","found particle inserting at"+particles_size);
					particles[particles_size].x = ix;
					particles[particles_size].y = iy;
					particles[particles_size].val = val;
					particles[particles_size].background=background;
					particles[particles_size].variance=variance;
					particles[particles_size].time = time; //System.currentTimeMillis();
					particles[particles_size].lon = location.getLongitude();
					particles[particles_size].lat = location.getLatitude();

					// look at the 8 adjacent pixels to measure max and ave values
					int dx[] = { +1,-1,0 , 0,-1,+1,+1,-1};
					int dy[] = {  0, 0,+1,-1,+1,-1,+1,-1};
					particles[particles_size].nearAve=0;
					particles[particles_size].nearAve25=0;

					particles[particles_size].nearMax=0;
					int count=0;
					for (int id=0;id<8;id++)
					{
					  int idx = ix + dx[id];
					  int idy = iy + dy[id];
					  if (idx>=0&&idy>=0&&ix<width&&iy<width)
					  {
						  int dval = vals[idx+width*idy]& 0xFF;
						  particles[particles_size].nearAve += dval;
						  if (dval>particles[particles_size].nearMax) particles[particles_size].nearMax = dval;
						  count++;
					  }
					}
					particles[particles_size].nearAve = particles[particles_size].nearAve/ ((float)count);
					
					// look at the 8 adjacent pixels to measure max and ave values
					int dx5[] = { -2,-2,-2,-2,-2,-1,-1,-1,-1,-1,0,0,0,0,1,1,1,1,1         ,2,2,2,2,2};
					int dy5[] = { +2,+1,0,-1,-2, +2,+1,0,-1,-2, +2,+1,-1,-2, +2,+1,0,-1,-2, +2,+1,0,-1,-2};
					int count5=0;
					for (int id=0;id<24;id++)
					{
					  int idx = ix + dx5[id];
					  int idy = iy + dy5[id];
					  if (idx>=0&&idy>=0&&ix<width&&iy<width)
					  {
						  int dval = vals[idx+width*idy]& 0xFF;
						  particles[particles_size].nearAve25 += dval;
						  count5++;
					  }
					}
					particles[particles_size].nearAve25 = particles[particles_size].nearAve25/ ((float)count5);
					
					particles_size++;
				}
			}
		
		
		
	}
	

}
