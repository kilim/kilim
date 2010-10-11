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

import kilim.Mailbox;
import kilim.Task;
import kilim.Pausable;


class DefaultProactor extends CompletionDispatcher{

    private final Mailbox<Act> completionQueue = new Mailbox<Act>();

    DefaultProactor(){}

    @Override
    public Mailbox<Act> completionQueue(){
        return this.completionQueue;
    }
    
    @Override
    public void execute() throws Pausable{
        while(true){
            //1. get act
            Act act = completionQueue.get(); // blocking
            new Notifier(act).start(); 
        }
    } 

    private static class Notifier extends Task{
        Act act;
        Notifier(Act act){
            this.act = act;
        }
        @Override
        public void execute() throws Pausable{
            //2. update result info 
            this.act.setStatus(Act.Status.Complete);
            //3. CompletionHandler.notify(AsyncOp, act)
            this.act.complete(); 
        }
    }
}
