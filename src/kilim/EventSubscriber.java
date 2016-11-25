// Copyright 2006 by sriram - offered under the terms of the MIT License

package kilim;

public interface EventSubscriber {
    void onEvent(EventPublisher ep, Event e);
}
