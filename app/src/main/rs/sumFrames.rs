#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation gSum;
rs_allocation gSsq;

float gMaxWeight;
uint sampleStep;
uint gTotalFrames;

// Since normalization is very succeptible to smallest bin, we
// add back the smallest mean that could result in a bin value
// of 0 within 99% confidence (assuming about 150 pixels, 10%
// of the downsampled grid, could conceivably be in that range).
// This comes out to -log(1/150) ~ 5

static uint gFudgeFactor = 5;


// keeps a running sum of frames
void RS_KERNEL update(uchar in, uint32_t x, uint32_t y) {

    int in32 = (int) in;

    int old_sum = rsGetElementAt_int(gSum, x, y);
    rsSetElementAt_int(gSum, old_sum + in32, x, y);

    int old_ssq = rsGetElementAt_int(gSsq, x, y);
    rsSetElementAt_int(gSsq, old_ssq + in32*in32, x, y);
}

float RS_KERNEL find_mean(uint32_t x, uint32_t y) {
    float sum = (float) rsGetElementAt_int(gSum, x, y);
    return sum / gTotalFrames;
}

float RS_KERNEL find_var(uint32_t x, uint32_t y) {
    float sum = (float) rsGetElementAt_int(gSum, x, y);
    float ssq = (float) rsGetElementAt_int(gSsq, x, y);
    float mean = sum / gTotalFrames;
    return ssq / gTotalFrames - mean*mean;
}

// calculate the unnormalized weights as log(1+1/val)
// and divide by gMaxWeights to obtain a float weight in the
// interval [0,1].
uchar RS_KERNEL normalizeWeights(float in) {
    float inv_mean = 1/in;
    float weight = log1p(inv_mean)/gMaxWeight;
    // store as a byte for compression
    return (uchar)(255*weight);
}

// downsample gSum into an Allocation of average pix_val's
float RS_KERNEL downsampleSums(uint32_t x, uint32_t y) {
    uint count = 0;
    uint sum = gFudgeFactor;
    // iterate over blocks of size sampleStep x sampleStep
    for(uint ix=x*sampleStep; ix<(x+1)*sampleStep; ix++) {
        for(uint iy=y*sampleStep; iy<(y+1)*sampleStep; iy++) {
            int isum = rsGetElementAt_int(gSum, ix, iy);
            // skip over hotcells marked as -1
            if(isum >= 0) {
                sum += isum;
                count++;
            }
        }
    }

    return (float)sum/count/gTotalFrames;
}

// mark hotcells as -1
void killHotcell(uint32_t x, uint32_t y) {
    rsSetElementAt_int(gSum, -1, x, y);
}