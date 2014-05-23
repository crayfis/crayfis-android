package edu.uci.crayfis;

/******************************************
 * 
 *  class to extract particle candidates from integrated camera frames
 * 
 */

import android.location.Location;
import android.util.Log;

import java.io.FileOutputStream;
import java.util.ArrayList;

import edu.uci.crayfis.DAQActivity.RawCameraFrame;
import edu.uci.crayfis.ParticleData;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.content.Context;

public class ParticleReco {
	ParticleData[] particles;
	
	public int[] histogram = new int[256];
	public int[] max_histogram = new int[256];
	
	public int histo_count = 0;
	public int max_histo_count = 0;
	
	int max_particles=80000;
	public ParticleReco(Camera.Size previewSize)
	{
		this.previewSize = previewSize;
		particles = new ParticleData[max_particles];
		good_quality=false;
		clearHistograms();
		for (int i=0;i<max_particles;i++)
			particles[i] = new ParticleData();
	}
	int particles_size=0;
	
	public boolean good_quality;
	public float background;
	public float variance;
	public int ncount;
	public int max_val;
	
	public long time;
	public Location location;
	
	public class RecoEvent {
		public long time;
		public Location location;
		
		public boolean quality;
		public float background;
		public float variance;
		
		public ArrayList<RecoPixel> pixels = new ArrayList<RecoPixel>();
	}
	
	public class RecoPixel {
		public int x, y;
		public int val;
		public float avg_3, avg_5;
		public int near_max;
	}
	
	public class Histogram {
		public int[] values;
		private boolean mean_valid = false;
		private boolean variance_valid = false;
		double mean = 0;
		double variance = 0;
		int integral = 0;
		final int nbins;
		
		public Histogram(int nbins) {
			if (nbins <= 0) {
				throw new RuntimeException("Hey dumbo a histogram should have at least one bin.");
			}
			
			this.nbins = nbins;
			
			// make two additional bins for under/overflow
			values = new int[nbins+2];
		}
		public void clear() {
			for (int i = 0; i < values.length; ++i) {
				values[i] = 0;
			}
			integral = 0;
			mean_valid = false;
			variance_valid = false;
		}
		
		public void fill(int val) {
			fill(val, 1);
		}
		public void fill(int x, int weight) {
			if (x < 0) {
				// underflow
				values[nbins] += weight;
			}
			else if (x >= nbins) {
				// overflow
				values[nbins+1] += weight;
			}
			else {
				values[x] += weight;
				integral += weight;
				mean_valid = false;
			}
		}
		
		public double getMean() {
			if (mean_valid)
				return mean;
			int sum = 0;
			for (int i = 0; i < nbins; ++i) {
				sum += values[i];
			}
			mean = ((double) sum) / integral;
			mean_valid = true;
			return mean;
		}
		
		public double getVariance() {
			if (variance_valid)
				return variance;
			mean = getMean();
			int sum = 0;
			for (int i = 0; i < nbins; ++i) {
				int val = values[i];
				sum += (val-background)*(val - background);
			}
			variance = ((double) sum) / integral;  
			variance_valid = true;
			return variance;
		}
	}
	
	public Histogram h_pixel = new Histogram(256);
	public Histogram h_l2pixel = new Histogram(256);
	public Histogram h_maxpixel = new Histogram(256);
	public Histogram h_numpixel = new Histogram(4000);
	
	// counters to keep track of how many events and pixels
	// have been built since the last reset.
	public int event_count = 0;
	public int pixel_count = 0;

	// reset the state of the ParticleReco object;
	// that means clearing histograms and counters
	public void reset() {
		clearHistograms();
		event_count = 0;
		pixel_count = 0;
	}
	
	public void clearHistograms() {
		h_pixel.clear();
		h_l2pixel.clear();
		h_maxpixel.clear();
		h_numpixel.clear();
		
		for (int i = 0; i<256; ++i) {
			histogram[i] = 0;
			max_histogram[i] = 0;
		}
		histo_count = 0;
		max_histo_count = 0;
	}
	
	// When measuring bg and variance, we only look at some pixels
	// to save time. stepW and stepH are the number of pixels skipped
	// between samples.
	public static final int stepW = 10;
	public static final int stepH = 10;
	
	public Camera.Size previewSize = null;

	public RecoEvent buildEvent(RawCameraFrame frame) {
		event_count++;
		
		RecoEvent event = new RecoEvent();
		
		event.time = frame.acq_time;
		event.location = frame.location;
		
		// first we measure the background and variance, but to save time only do it for every
		// stepW or stepH-th pixel
		
		// get mean background level 
		float background = 0;
		float variance = 0;
		
		int width = previewSize.width;
		int height = previewSize.height;
		
		int npixels = 0;
		
		byte[] bytes = frame.bytes;
		
		// TODO: see if we can do this more efficiently
		// (there is a one-pass algorithm but it may not be stable)
		
		// calculate mean background value
		for (int ix = 0; ix < width; ix += stepW) {
			for (int iy = 0; iy < height; iy+=stepH) {
				int val = bytes[ix+width*iy]&0xFF; 
				background += (float)val;
				npixels++;
			}
		}
		if (npixels > 0) {
		  background = (float)background/((float)1.0*ncount);
		}
		
		// calculate variance
		for (int ix=0;ix < width;ix+= stepW)
			for (int iy=0;iy<height;iy+=stepH)
			{
				int val = bytes[ix+width*iy]&0xFF; 
				variance += (val-background)*(val - background);
				ncount++;
				//Log.d("particlereco",
					//	"background("+ix+","+iy+") = "+val+" var("+ncount+") = "
						//		+(float)Math.sqrt((float)variance/((float)1.0*ncount)));
			}
		if (ncount>0)
		  variance = (float)Math.sqrt((float)variance/((float)1.0*ncount));
		
		// is the data good?
		// TODO: investigate what makes sense here!
		good_quality = (background < 30 && variance < 5);
		
		Log.d("reco","background = "+background+" var = "+variance+" qual = "+good_quality);
		
		event.background = background;
		event.variance = variance;
		event.quality = good_quality;
		
		return event;
	}
	
	public ArrayList<RecoPixel> buildL2Pixels(RawCameraFrame frame, int thresh) {
		return buildL2Pixels(frame, thresh, false);
	}
	
	public ArrayList<RecoPixel> buildL2Pixels(RawCameraFrame frame, int thresh, boolean quick) {
		ArrayList<RecoPixel> pixels = null;
		
		if (! quick) {
			pixels = new ArrayList<RecoPixel>();
		}
		
		int width = previewSize.width;
		int height = previewSize.height;
		int max_val = 0;
		int num_pix = 0;
		
		byte[] bytes = frame.bytes;
		
		for (int ix=0; ix < width; ix++) {
			for (int iy=0; iy < height; iy++) {
				// NB: cast (signed) byte to integer for meaningful comparisons!
				int val = bytes[ix+width*iy] & 0xFF; 		
				
				h_pixel.fill(val);
				
				if (val > max_val) {
					max_val = val;
				}
				
				if (val > thresh)
				{
					h_l2pixel.fill(val);
					num_pix++;
					
					if (quick) {
						// okay, bail out without any further reco
						continue;
					}
					
					// okay, found a pixel above threshold!
					RecoPixel p = new RecoPixel();
					p.x = ix;
					p.y = iy;
					p.val = val;
					
					// look at the 8 adjacent pixels to measure max and ave values
					int sum3 = 0, sum5 = 0;
					int norm3 = 0, norm5 = 0;
					for (int dx = -2; dx <= 2; ++dx) {
						for (int dy = -2; dy <= 2; ++dy) {
							if (dx == 0 && dy == 0) {
								// exclude center from average
								continue;
							}
							
							int idx = ix + dx;
							int idy = iy + dy;
							if (idx < 0 || idy < 0 || idx >= width || idy >= height) {
								// we're off the sensor plane.
								continue;
							}
							
							int dval = bytes[idx + width*idy] & 0xFF;
							sum5 += dval;
							norm5++;
							if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
								// we're in the 3x3 part
								sum3 += dval;
								norm3++;
							}
						}
					}
					
					p.avg_3 = ((float) sum3) / norm3;
					p.avg_5 = ((float) sum5) / norm5;
					
					pixels.add(p);
				}
			}
		}
		
		// update the event-level histograms.
		h_maxpixel.fill(max_val);
		h_numpixel.fill(num_pix);
		
		pixel_count += num_pix;
		
		return pixels;
	}
	
	// take an image and look for hits
	public void process(byte[] vals, int width, int height, int thresh, Location location, long time)
	{
		this.time = time;
		this.location = location;
		
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
		
		for (int ix=0;ix < width;ix++) {
			for (int iy=0;iy<height;iy++)
			{
				int val = vals[ix+width*iy]&0xFF; 		
				histogram[val]++;
				histo_count++;
				
				if (val > max_val) {
					max_val = val;
				}
				
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
		
		max_histogram[max_val] += 1;
		max_histo_count++;
	}
	
	public int calculateThresholdByEvents(int max_count) {
		// return a the lowest threshold that will give fewer than
		// max_count events over the integrated period contained
		// in the histograms.
		
		Log.i("calculate", "Cacluating threshold! Event histo:"
				+ " 0 - " + h_maxpixel.values[0] + "\n"
				+ " 1 - " + h_maxpixel.values[1] + "\n"
				+ " 2 - " + h_maxpixel.values[2] + "\n"
				+ " 3 - " + h_maxpixel.values[3] + "\n"
				+ " 4 - " + h_maxpixel.values[4] + "\n"
				);
		Log.i("calculate", "Pixel histo:"
				+ " 0 -" + histogram[0] + "\n"
				+ " 1 -" + histogram[1] + "\n"
				+ " 2 -" + histogram[2] + "\n"
				+ " 3 -" + histogram[3] + "\n"
				+ " 4 -" + histogram[4] + "\n"
				);
		
		if (h_maxpixel.integral == 0) {
			// wow! no data, either we didn't expose long enough, or
			// the background is super low. Return 0 threshold.
			return 0;
		}
		
		int integral = 0;
		int new_thresh;
		for (new_thresh = 255; new_thresh >= 0; --new_thresh) {
			integral += h_maxpixel.values[new_thresh];
			if (integral > max_count) {
				// oops! we've gone over the limit.
				// bump the threshold back up and break out.
				new_thresh++;
				break;
			}
		}
		
		return new_thresh;
	}
	
	public int calculateThresholdByPixels(int stat_factor) {
		int new_thresh = 255;
		// number of photons above this threshold
		int above_thresh = 0;
		do {
			above_thresh += histogram[new_thresh];
			Log.d("calibration", "threshold " + new_thresh
					+ " obs= " + above_thresh + "/" + stat_factor);
			new_thresh--;
		} while (new_thresh > 0 && above_thresh < stat_factor);
		
		return new_thresh;
	}

}
