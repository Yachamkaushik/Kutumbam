package com.kutumbam.app

import com.kutumbam.app.parse.RangeCheck
import com.kutumbam.app.parse.RangeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeCheckTest {
    @Test fun twoSidedRange() {
        assertEquals(RangeStatus.BELOW, RangeCheck.status(11.2, 13.0, 17.0))
        assertEquals(RangeStatus.IN_RANGE, RangeCheck.status(13.0, 13.0, 17.0))
        assertEquals(RangeStatus.IN_RANGE, RangeCheck.status(17.0, 13.0, 17.0))
        assertEquals(RangeStatus.ABOVE, RangeCheck.status(142.0, 70.0, 100.0))
    }

    @Test fun oneSidedRanges() {
        assertEquals(RangeStatus.ABOVE, RangeCheck.status(212.0, null, 200.0))
        assertEquals(RangeStatus.IN_RANGE, RangeCheck.status(150.0, null, 200.0))
        assertEquals(RangeStatus.BELOW, RangeCheck.status(38.0, 40.0, null))
    }

    @Test fun noPrintedRangeMeansNoJudgement() {
        assertEquals(RangeStatus.NO_RANGE, RangeCheck.status(7.4, null, null))
    }
}
