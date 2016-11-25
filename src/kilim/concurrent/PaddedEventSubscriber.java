// Copyright 2014 nilangshah - offered under the terms of the MIT License

package kilim.concurrent;

import kilim.EventSubscriber;

abstract class EventSubCellPrePad {
    long p0, p1, p2, p3, p4, p5, p6;
}

abstract class EventSubCellValue extends EventSubCellPrePad {
    public EventSubscriber value;
}

public class PaddedEventSubscriber extends EventSubCellValue {
    int i0;
    long p10, p11, p12, p13, p14, p15, p16;

    public PaddedEventSubscriber() {

    }

    public EventSubscriber get() {
        return value;
    }

    public void set(EventSubscriber e) {
        value = e;
    }

}
