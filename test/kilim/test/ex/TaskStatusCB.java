package kilim.test.ex;

public interface TaskStatusCB {
    void beforeYield();
    void afterYield();
    void done();
}
