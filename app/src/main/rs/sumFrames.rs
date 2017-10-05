#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation gSum;

// keeps a running sum of frames
void RS_KERNEL update(uchar in, uint32_t x, uint32_t y) {
    int old_weight = rsGetElementAt_int(gSum, x, y);
    rsSetElementAt_int(gSum, old_weight + in, x, y);
}