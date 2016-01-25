package com.wakebyte.aerocharger;

/**
 * Created by Will on 4/23/15.
 */

import android.content.Context;

import com.openxc.measurements.Measurement;
import com.openxc.messages.SimpleVehicleMessage;
import com.openxc.units.Unit;
import com.openxc.util.Range;

public class ChargerStatus implements Measurement{
    @Override
    public long getAge() {
        return 0;
    }

    @Override
    public void setTimestamp(long timestamp) {

    }

    @Override
    public boolean hasRange() {
        return false;
    }

    @Override
    public Range<? extends Unit> getRange() {
        return null;
    }

    @Override
    public Unit getValue() {
        return null;
    }

    @Override
    public SimpleVehicleMessage toVehicleMessage() {
        return null;
    }

    @Override
    public long getBirthtime() {
        return 0;
    }

    @Override
    public String getName(Context context) {
        return null;
    }

    @Override
    public String getGenericName() {
        return null;
    }

    @Override
    public Object getSerializedValue() {
        return null;
    }
}
