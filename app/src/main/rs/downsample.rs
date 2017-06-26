#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gSum;
uint gTotalFrames;
float gMinSum;
static const float gOffset = 0.5; // maximum value that camera is presumed to round down
uint sampleStep;

uchar RS_KERNEL normalizeWeights(uint in) {
    return (uchar)(255*gMinSum/(in + gTotalFrames*gOffset*sampleStep*sampleStep));
}

uint RS_KERNEL downsampleSums(uint32_t x, uint32_t y) {
    uint sum = 0;
    uint maxSum = 0;
    for(uint ix=x*sampleStep; ix<(x+1)*sampleStep; ix++) {
        for(uint iy=y*sampleStep; iy<(y+1)*sampleStep; iy++) {
            uint isum = rsGetElementAt_uint(gSum, ix, iy);
            sum += isum;
            if(isum > maxSum) {
                maxSum = isum;
            }
        }
    }
    return sum;
}