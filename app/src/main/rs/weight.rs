#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gWeights;
rs_script gScript;
uint n_frames = 0;
uint gTotalFrames = 1000;
static const float gNormalizedVal = 10;
static const float gOffset = 0.5;

uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    return (uchar)(in * rsGetElementAt_float(gWeights, x, y));
}

void RS_KERNEL update(const uchar in, uint32_t x, uint32_t y) {
    float old_weight = rsGetElementAt_float(gWeights, x, y);
    float new_weight = old_weight + in;
    rsSetElementAt_float(gWeights, new_weight, x, y);
}

float RS_KERNEL normalizeWeights(float in) {
    return gNormalizedVal/(in/gTotalFrames + gOffset);
}

void update_weights(rs_allocation frame) {
    rs_allocation ignoredOut;
    rsForEach(update, frame);
    n_frames++;

    if(n_frames == gTotalFrames) {
        rsForEach(normalizeWeights, gWeights, gWeights);
        n_frames = 0;
    }
}
