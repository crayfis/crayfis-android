#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gWeights;
uint gTotalFrames;
float gMinSum;
static const float gOffset = 0.5; // maximum value that camera is presumed to round down
uint sampleStep;

float RS_KERNEL normalizeWeights(float in) {
    return gMinSum/(in + gTotalFrames*gOffset);
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

void killHotcell(uint32_t x, uint32_t y) {
    rsSetElementAt_float(gWeights, -1.0, x, y);
}