#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation gInYuv;

uchar RS_KERNEL parseY(uint32_t x, uint32_t y) {
    return rsGetElementAtYuv_uchar_Y(gInYuv, x, y);
}

uchar RS_KERNEL truncateRAW(ushort rawval) {
    return (uchar) min(rawval, (ushort)255);
}