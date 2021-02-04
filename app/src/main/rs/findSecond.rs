#pragma version(1)
#pragma rs java_package_name(io.crayfis.android)
#pragma rs_fp_relaxed

rs_allocation aMax;
rs_allocation aSecond;

// given the largest value, second largest value, and a new allocation,
// updates the ordering
void RS_KERNEL order_uchar(uchar in, uchar wgt, uint32_t x, uint32_t y) {

    uchar one = rsGetElementAt_uchar(aMax, x, y);
    uchar two = rsGetElementAt_uchar(aSecond, x, y);

    uchar adj = (uchar) ((uint32_t) in * wgt / 255);

    if(adj > two) {

        two = adj;
        if(two > one) {
            two = one;
            one = adj;
        }
        rsSetElementAt_uchar(aMax, one, x, y);
        rsSetElementAt_uchar(aSecond, two, x, y);
    }

}

void RS_KERNEL order_ushort(ushort in, uchar wgt, uint32_t x, uint32_t y) {

    // for finding hotcells, we shouldn't need the extra bits of precision
    uchar adj = (uchar) max((uint32_t)in * wgt / 255, (uint32_t)255);

    uchar one = rsGetElementAt_uchar(aMax, x, y);
    uchar two = rsGetElementAt_uchar(aSecond, x, y);

    if(adj > two) {

        two = adj;
        if(two > one) {
            two = one;
            one = adj;
        }
        rsSetElementAt_uchar(aMax, one, x, y);
        rsSetElementAt_uchar(aSecond, two, x, y);
    }

}