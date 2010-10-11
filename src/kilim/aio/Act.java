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

public final class Act{

    private Status status;
    private final CompletionHandler handler;

    public static enum Status{
        Complete(1),
        Cancel(0);
        int s;
        Status(int s){
            this.s = s;
        }
        public int value(){ return this.s; }
    }

    public Act(CompletionHandler handler){
        this.handler = handler;
    }

    public CompletionHandler getCompletionHandler(){
        return this.handler;
    }

    public Status getStatus(){
        return this.status;
    }

    public void setStatus(Status status){
        this.status = status;
    }

    public void complete(){
        try{
            this.handler.notify(this);
        }catch(IOException ioe){
            ioe.printStackTrace();
        }
    }
}
