package kilim;

public interface EventSubscriber {
    void onEvent(EventPublisher ep, Event e);
}
