#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gSum;

int RS_KERNEL update(uchar in, uint32_t x, uint32_t y) {
    int old_weight = rsGetElementAt_int(gSum, x, y);
    return old_weight + in;
}