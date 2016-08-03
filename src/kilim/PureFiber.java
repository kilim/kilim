// copyright 2016 seth lytle
package kilim;




public abstract class PureFiber {
    private static Fiber.MethodRef runnerInfo = new Fiber.MethodRef(PureFiber.class.getName(),"run");
    static final FakeTask fakeTask = new FakeTask();
    private static class FakeTask extends Task {
        Fiber.MethodRef getRunnerInfo() {
            return runnerInfo;
        }
    }

    public Exception ex;
    private kilim.Fiber fiber = new kilim.Fiber(fakeTask);
    public boolean run() throws kilim.NotPausable {
        try {
            fiber.begin();
            execute( fiber );
        }
        catch (Exception        kex) { ex = kex; }
        boolean ret = ex==null ? fiber.end() : true;
        return ret;
    }
    public void execute() throws Pausable, Exception {
        Task.errNotWoven();
    }
    public void execute(kilim.Fiber fiber) {} // fixme:dry - keeps netbeans run-single happy :(

    public void reset() { ex = null; fiber.reset(); }
}
