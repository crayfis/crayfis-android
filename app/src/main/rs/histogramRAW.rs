#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

uint32_t* ahist;

static const float gOffset = 0.5;
static const uchar gOffsetByte = (uchar)(gOffset * 255);
static const uchar gMaxByte = (uchar)255;

void RS_KERNEL histogram_unweighted(ushort val, uint32_t x, uint32_t y) {
    volatile uint32_t* addr = &ahist[val];
    rsAtomicInc(addr);
}

void RS_KERNEL histogram_weighted(ushort val, uchar wgt, uint32_t x, uint32_t y) {
    uint adj = ((uint)val * wgt + gOffsetByte) / gMaxByte;
    volatile uint32_t* addr = &ahist[adj];
    rsAtomicInc(addr);
}

void clear() {
    for(int i=0; i<1024; i++) {
        *(ahist + i) = 0;
    }
}