package com.leapmotion.leap;

import java.util.List;

/** @author Tiago Martins */
public class DeviceList extends LeapList<Device> {

    public DeviceList() { super(null); }

    DeviceList(List<Device> d) { super(d); }

    @Override
    protected Device invalidItem() { return Device.invalid(); }
}
