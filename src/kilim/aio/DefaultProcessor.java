/* Copyright (c) 2010 ChiaHung Lin
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

import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;
import kilim.ExitMsg;

class DefaultProcessor extends AsynchronousOperationProcessor{

    private final Mailbox<Act> queue = new Mailbox<Act>();

    DefaultProcessor(){}

    @Override
    public void register(CompletionHandler handler){ // initiator, client
        if(null == handler) 
            throw new NullPointerException("Handler is not provided.");
        queue.putnb(new Act(handler));
    }
       
    @Override
    public void execute() throws Pausable{
        while(true){
            Act act = queue.get(); // blocking
            Mailbox<Act> in = new Mailbox<Act>(); 
            in.putnb(act);
            new AsyncOpWorker(in).start();
        }
    }

    private class AsyncOpWorker extends Task{
        Mailbox<Act> in;
        AsyncOpWorker(Mailbox<Act> in){
            this.in = in;
        } 

        @Override
        public void execute() throws Pausable{
            Act act = in.get();
            Mailbox<ExitMsg> exit = new Mailbox<ExitMsg>(); 
            // underlying async op impl should set result object
            MulticastSocketHandler handler = 
                (MulticastSocketHandler)act.getCompletionHandler();
            CompletionDispatcher dispatcher = handler.getCompletionDispatcher();
            AsynchronousOperation op = handler.getAsynchronousOperation();
            op.start().informOnExit(exit);
            exit.getb(); // (thread) blocking 
            dispatcher.completionQueue().putnb(act); 
        }
    } 
}
