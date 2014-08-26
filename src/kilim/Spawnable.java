package kilim;


/**
 * Meant to supply a body to {@code Task#spawn(Spawnable)} 
 */
@FunctionalInterface
public interface Spawnable {
    void execute() throws Pausable;
}
