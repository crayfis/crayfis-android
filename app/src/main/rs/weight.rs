#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation gIn;
static const float gOffset = 0.5;
static const uchar gOffsetByte = (uchar)(gOffset * 255);
static const uchar gMaxByte = (uchar)255;

uchar RS_KERNEL weightYUV(uchar wgt, uint32_t x, uint32_t y) {
    uint in = (uint)rsGetElementAtYuv_uchar_Y(gIn, x, y);
    return (uchar)((in * wgt + gOffsetByte)/gMaxByte);
}