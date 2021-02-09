#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation aMax;
rs_allocation aSecond;
rs_allocation gWeights;

static void reorder(uchar in, uint32_t x, uint32_t y) {

    uchar one = rsGetElementAt_uchar(aMax, x, y);
    uchar two = rsGetElementAt_uchar(aSecond, x, y);

    if(in > two) {

        two = in;
        if(two > one) {
            two = one;
            one = in;
        }
        rsSetElementAt_uchar(aMax, one, x, y);
        rsSetElementAt_uchar(aSecond, two, x, y);
    }
}

// given the largest value, second largest value, and a new allocation,
// updates the ordering
void RS_KERNEL order_weighted_uchar(uchar in, uint32_t x, uint32_t y) {

    uchar wgt = rsGetElementAt_uchar(gWeights, x, y);
    uchar adj = (uchar) ((uint32_t) in * wgt / 255);

    reorder(adj, x, y);

}

void RS_KERNEL order_weighted_ushort(ushort in, uint32_t x, uint32_t y) {
    uchar wgt = rsGetElementAt_uchar(gWeights, x, y);
    // for finding hotcells, we shouldn't need the extra bits of precision
    uchar adj = (uchar) max((uint32_t)in * wgt / 255, (uint32_t)255);

    reorder(adj, x, y);

}

void RS_KERNEL order_uchar(uchar in, uint32_t x, uint32_t y) {
    reorder(in, x, y);
}

void RS_KERNEL order_ushort(ushort in, uint32_t x, uint32_t y) {
    uchar in8 = (uchar)(max((uint32_t)in, (uint32_t)255));
    reorder(in8, x, y);
}

