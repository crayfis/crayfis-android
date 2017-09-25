#pragma version(1)
#pragma rs java_package_name(edu.uci.crayfis)
#pragma rs_fp_relaxed

rs_allocation aMax;
rs_allocation aSecond;

// given the largest value, second largest value, and a new allocation,
// updates the ordering
void RS_KERNEL order(uchar in, uint32_t x, uint32_t y) {

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