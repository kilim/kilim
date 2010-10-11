/* 
 * Copyright (c) 2010 ChiaHung Lin
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package kilim.aio;

import java.io.IOException;

public abstract class MulticastSocketHandler extends AbstractCompletionHandler{

    private final AsynchronousOperation operation;

    public MulticastSocketHandler(CompletionDispatcher dispatcher, 
                                  String address, int port,
                                  Class<? extends MulticastSocketReceiver> receiver){
        super(dispatcher); // not yet started
        this.operation = new MulticastSocketOperation(
            address, port, receiver 
        );
        if(null == dispatcher) 
            throw new NullPointerException("Proactor is not provided.");
    }

    public AsynchronousOperation getAsynchronousOperation(){
        return this.operation;
    }
    
    public void receive() {
        AsynchronousOperationProcessor p = AioFactory.createDefaultProcessor();
        p.register(this);
        p.start();
    }

    public abstract void notify(Act act) throws IOException;
}
