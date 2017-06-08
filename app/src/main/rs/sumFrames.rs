#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gSum;

float RS_KERNEL update(uchar in, uint32_t x, uint32_t y) {
    float old_weight = rsGetElementAt_float(gSum, x, y);
    return old_weight + in;
}