#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gSum;
float gMaxWeight;
uint sampleStep;
uint gTotalFrames;

// Since normalization is very succeptible to smallest bin, we
// add back the smallest mean that could result in a bin value
// of 0 within 99% confidence (assuming about 150 pixels, 10%
// of the downsampled grid, could conceivably be in that range).
// This comes out to -log(1/150) ~ 5

static uint gFudgeFactor = 5;

uchar RS_KERNEL normalizeWeights(float in) {
    float inv_mean = 1/in;
    float weight = log1p(inv_mean)/gMaxWeight;
    //rsDebug("weight =",weight);
    return (uchar)(255*weight);
}

float RS_KERNEL downsampleSums(uint32_t x, uint32_t y) {
    uint count = 0;
    uint sum = gFudgeFactor;
    for(uint ix=x*sampleStep; ix<(x+1)*sampleStep; ix++) {
        for(uint iy=y*sampleStep; iy<(y+1)*sampleStep; iy++) {
            int isum = rsGetElementAt_int(gSum, ix, iy);
            if(isum >= 0) {
                sum += isum;
                count++;
            }
        }
    }

    return (float)sum/count/gTotalFrames;
}

void killHotcell(uint32_t x, uint32_t y) {
    rsSetElementAt_int(gSum, -1, x, y);
}