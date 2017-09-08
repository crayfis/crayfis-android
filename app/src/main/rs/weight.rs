#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation gWeights;
rs_allocation gInYuv;
static const float gOffset = 0.5; // maximum value that camera is presumed to round down


uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    return (uchar)(in * rsGetElementAt_float(gWeights, x, y)+gOffset);
}

uchar RS_KERNEL weightYuv(uint32_t x, uint32_t y) {
    return (uchar)(rsGetElementAtYuv_uchar_Y(gInYuv, x, y) * rsGetElementAt_float(gWeights, x, y) + gOffset);
}