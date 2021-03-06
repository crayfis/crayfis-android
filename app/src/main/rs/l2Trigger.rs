#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation gWeights;

uint32_t* gPixIdx; // these need to be 32-bit, otherwise the addresses get complicated
uint32_t* gPixVal;
uint32_t* gPixN;

uint gNPixMax;
bool gMaxN;
uint gResX;

// set this separately
static uint gL2Thresh;

static const float gOffset = 0.5; // maximum value that camera is presumed to round down
static const uchar gOffsetByte = (uchar) (gOffset * 255);
static const uchar gMaxByte = (uchar) 255;

void set_L2Thresh(int l2Thresh) {
    gL2Thresh = (l2Thresh+1) * gMaxByte - gOffsetByte;
}

// multiplies input allocation by weights and rounds
static void trigger_default(uint in, uchar wgt, uint32_t x, uint32_t y) {
    uint adjusted = in * wgt;
    //rsAtomicMax(&gMax, adjusted);
    if (adjusted >= gL2Thresh) {
        //rsDebug("weighted:", (1.0 * adjusted + gOffsetByte) / gMaxByte);
        uint pixN = rsAtomicInc(gPixN);
        if(pixN < gNPixMax) {
            uint32_t idx = x + gResX*y;
            *(gPixIdx+pixN) = idx;
        }
    }
}

static void trigger_maxn(uint in, uchar wgt, uint32_t x, uint32_t y) {

    uint adjusted = (uint)in * wgt;

    if (adjusted > gL2Thresh && (*gPixN < gNPixMax || adjusted > *(gPixVal + gNPixMax - 1))) {

        uint pixN = rsAtomicInc(gPixN);
        uint iidx = x + gResX*y;
        uint ival = adjusted;

        for(uint j=0; j<pixN; j++) {
            int jidx = *(gPixIdx + j);
            int jval = *(gPixVal + j);

            if(ival > jval) {
               *(gPixIdx+j) = iidx;
               *(gPixVal+j) = ival;

               iidx = jidx;
               ival = jval;
            }
        }
    }
}

void RS_KERNEL trigger_uchar(uchar in, uint32_t x, uint32_t y) {
    uchar wgt = rsGetElementAt_uchar(gWeights, x, y);
    if(gMaxN) {
        trigger_maxn((uint) in, wgt, x, y);
    } else {
        trigger_default((uint) in, wgt, x, y);
    }

}

void RS_KERNEL trigger_ushort(ushort in, uint32_t x, uint32_t y) {
    uchar wgt = rsGetElementAt_uchar(gWeights, x, y);
    if(gMaxN) {
        trigger_maxn((uint) in, wgt, x, y);
    } else {
        trigger_default((uint) in, wgt, x, y);
    }
}

void reset() {
    *gPixN = 0;
}