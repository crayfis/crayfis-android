#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation weights;

uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    return (uchar)(in * rsGetElementAt_float(weights, x, y));
}

void RS_KERNEL update_weights(uchar in, uint32_t x, uint32_t y) {
    float old_weight = rsGetElementAt_float(weights, x, y);

}
