#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation gWeights;
rs_allocation gInYuv;
static const float gOffset = 0.5; // maximum value that camera is presumed to round down
static const uchar gOffsetByte = (uchar)(gOffset * 255);
static const uchar gMaxByte = (uchar)255;

// multiplies input allocation by weights and rounds
uchar RS_KERNEL weight(uchar in, uint32_t x, uint32_t y) {
    return ((uint)in * rsGetElementAt_uchar(gWeights, x, y) + gOffsetByte)/gMaxByte;
}

// same as above, but for a YUV format input allocation
uchar RS_KERNEL weightYuv(uint32_t x, uint32_t y) {
    return ((uint)rsGetElementAtYuv_uchar_Y(gInYuv, x, y) * rsGetElementAt_uchar(gWeights, x, y) + gOffsetByte)/gMaxByte;
}

uchar RS_KERNEL weightRAW(ushort in, uint32_t x, uint32_t y) {
    ushort weighted = (in * rsGetElementAt_uchar(gWeights, x, y) + gOffsetByte)/255;
    return (uchar) min(weighted, (ushort)255);
}