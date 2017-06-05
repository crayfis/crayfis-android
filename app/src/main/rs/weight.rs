#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gWeights;
rs_allocation gSampled;
uint n_frames = 0;
uint gTotalFrames = 1000;
uint gMinSum;
static const float gOffset = 0.5; // maximum value that camera is presumed to round down

uint sampleStep;

void init() {
    gMinSum = 256 * gTotalFrames;
}

uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    return (uchar)(in * rsGetElementAt_float(gWeights, x, y)+gOffset);
}

float RS_KERNEL update(uchar in, uint32_t x, uint32_t y) {
    float old_weight = rsGetElementAt_float(gWeights, x, y);
    return old_weight + in;
}

float RS_KERNEL normalizeWeights(float in) {
    return gMinSum/(in + gTotalFrames*gOffset);
}

void RS_KERNEL findMin(float in) {
    float minSum = in + gTotalFrames * gOffset;
    if(minSum < gMinSum) {
        gMinSum = minSum;
    }
}

float RS_KERNEL downsampleWeights(uint32_t x, uint32_t y) {
    float sum = 0;
    for(int ix=x*sampleStep; ix<(x+1)*sampleStep; ix++) {
        for(int iy=y*sampleStep; iy<(y+1)*sampleStep; iy++) {
            sum += rsGetElementAt_float(gWeights, ix, iy);
        }
    }
    return sum/(sampleStep*sampleStep);

}

float RS_KERNEL resampleWeights(float weight, uint32_t x, uint32_t y) {
    return rsGetElementAt_float(gSampled, x/sampleStep, y/sampleStep);
}

