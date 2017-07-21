#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gSum;
float gPixPerSample;
float gMaxWeight;
uint sampleStep;

uchar RS_KERNEL normalizeWeights(uint in) {
    float inv_mean = gPixPerSample/++in;
    float weight = log1p(inv_mean)/gMaxWeight;
    //rsDebug("weight =",weight);
    return (uchar)(255*weight);
}

uint RS_KERNEL downsampleSums(uint32_t x, uint32_t y) {
    uint sum = 0;
    for(uint ix=x*sampleStep; ix<(x+1)*sampleStep; ix++) {
        for(uint iy=y*sampleStep; iy<(y+1)*sampleStep; iy++) {
            uint isum = rsGetElementAt_uint(gSum, ix, iy);
            sum += isum;
        }
    }
    //rsDebug("sum = ", sum);
    return sum;
}