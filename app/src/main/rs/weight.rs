#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gWeights;
rs_script gScript;
uint n_frames = 0;

uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    return (uchar)(in * rsGetElementAt_float(gWeights, x, y));
}

void root(const uchar4 *v_in, const uchar4 *v_out, uint32_t x, uint32_t y) {
    float old_weight = rsGetElementAt_float(gWeights, x, y);
    float new_weight = old_weight*(n_frames)/(n_frames+1);
    rsSetElementAt_float(gWeights, new_weight, x, y);
}

void update_weights(rs_allocation frame) {
    rsDebug("Yay!",n_frames);
    rs_allocation ignoredOut;
    //rsForEach(gScript, frame, ignoredOut);
    n_frames++;
}
