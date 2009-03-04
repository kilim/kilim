package kilim;

public class Event {
    /**
     * Type of event to switch on. The first 1-1000 are reserved for kilim
     * If you define your own eventType, make sure there are no collisions
     * with other projects. One strategy to reduce collisions is to take
     * the ascii codes of the first four consonants of your project's name
     * 
     */
    public final int eventType; 
    public Event(int evType) {
        eventType = evType;
    }
}
