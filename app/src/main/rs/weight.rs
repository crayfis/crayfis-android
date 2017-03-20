#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation weights;

uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    uchar out = in * rsGetElementAt_char(weights, x, y);
    return out;
}
